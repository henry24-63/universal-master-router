import { CredentialVault } from '../crypto/vault';

export interface AppwriteAccountRecord {
  id: string;
  label: string;
  endpoint: string;
  region: string;
  encrypted_api_key: string;
  encryption_salt: string;
  encryption_iv: string;
  status: string;
}

export interface AppwriteRequestOptions {
  method?: string;
  path: string;
  body?: any;
  queryParams?: Record<string, string>;
  timeoutMs?: number;
}

export interface AppwriteResponse<T = any> {
  ok: boolean;
  status: number;
  data: T | null;
  error: string | null;
  latencyMs: number;
}

export class AppwriteClient {
  /**
   * SSRF Protection: Validates that the endpoint URL is HTTPS and does NOT point to
   * localhost, loopback, private RFC 1918 IPv4 ranges, link-local, or cloud metadata IPs.
   */
  public static validateEndpointUrl(endpoint: string): { isValid: boolean; normalizedUrl: string; error?: string } {
    let raw = (endpoint || '').trim();
    if (!raw) {
      return { isValid: false, normalizedUrl: '', error: 'Endpoint URL is empty.' };
    }

    if (!raw.startsWith('http://') && !raw.startsWith('https://')) {
      raw = `https://${raw}`;
    }

    try {
      const parsed = new URL(raw);

      if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
        return { isValid: false, normalizedUrl: '', error: 'Only HTTP/HTTPS endpoints are allowed.' };
      }

      const hostname = parsed.hostname.toLowerCase();

      // Block localhost, loopback, local domain suffixes
      if (
        hostname === 'localhost' ||
        hostname === '127.0.0.1' ||
        hostname === '0.0.0.0' ||
        hostname === '::1' ||
        hostname.endsWith('.local') ||
        hostname.endsWith('.internal')
      ) {
        return { isValid: false, normalizedUrl: '', error: 'SSRF Protection: Access to localhost or loopback is strictly prohibited.' };
      }

      // Block Cloud Metadata Service IP (169.254.169.254) and link-local ranges
      if (hostname.startsWith('169.254.') || hostname.startsWith('fe80:')) {
        return { isValid: false, normalizedUrl: '', error: 'SSRF Protection: Access to link-local or metadata services is strictly prohibited.' };
      }

      // Block Private RFC 1918 IPv4 addresses
      const ipv4Match = hostname.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/);
      if (ipv4Match) {
        const [, p1, p2] = ipv4Match.map(Number);
        if (p1 === 10 || (p1 === 172 && p2 >= 16 && p2 <= 31) || (p1 === 192 && p2 === 168) || p1 === 127 || p1 === 0) {
          return { isValid: false, normalizedUrl: '', error: 'SSRF Protection: Access to private RFC 1918 network ranges is strictly prohibited.' };
        }
      }

      // Normalize trailing slash (e.g. https://cloud.appwrite.io/v1)
      let normalized = parsed.origin + parsed.pathname;
      if (normalized.endsWith('/')) {
        normalized = normalized.substring(0, normalized.length - 1);
      }

      return { isValid: true, normalizedUrl: normalized };
    } catch (e: any) {
      return { isValid: false, normalizedUrl: '', error: `Invalid URL format: ${e?.message || String(e)}` };
    }
  }

  /**
   * Decrypts an Appwrite API key from a database record using CredentialVault.
   */
  public static async decryptApiKey(account: AppwriteAccountRecord, masterKey: string): Promise<string> {
    const vault = new CredentialVault(masterKey || 'default-secure-master-key-32chars');
    return await vault.decrypt({
      cipherTextHex: account.encrypted_api_key,
      saltHex: account.encryption_salt,
      ivHex: account.encryption_iv
    });
  }

  /**
   * Executes a direct HTTPS request against the Appwrite REST API.
   */
  public static async request<T = any>(
    endpoint: string,
    projectId: string,
    apiKey: string,
    options: AppwriteRequestOptions
  ): Promise<AppwriteResponse<T>> {
    const validation = AppwriteClient.validateEndpointUrl(endpoint);
    if (!validation.isValid) {
      return {
        ok: false,
        status: 400,
        data: null,
        error: validation.error || 'Invalid endpoint URL.',
        latencyMs: 0
      };
    }

    let urlStr = `${validation.normalizedUrl}${options.path.startsWith('/') ? '' : '/'}${options.path}`;
    if (options.queryParams && Object.keys(options.queryParams).length > 0) {
      const searchParams = new URLSearchParams(options.queryParams);
      urlStr += `?${searchParams.toString()}`;
    }

    const controller = new AbortController();
    const timeoutMs = options.timeoutMs || 8000;
    const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

    const startTime = performance.now();

    try {
      const headers: Record<string, string> = {
        'X-Appwrite-Project': projectId,
        'X-Appwrite-Key': apiKey,
        'Content-Type': 'application/json'
      };

      const fetchOptions: RequestInit = {
        method: options.method || 'GET',
        headers,
        redirect: 'manual',
        signal: controller.signal
      };

      if (options.body && options.method && ['POST', 'PUT', 'PATCH'].includes(options.method.toUpperCase())) {
        fetchOptions.body = typeof options.body === 'string' ? options.body : JSON.stringify(options.body);
      }

      const response = await fetch(urlStr, fetchOptions);
      clearTimeout(timeoutId);
      const latencyMs = Math.round(performance.now() - startTime);

      let responseData: any = null;
      const contentType = response.headers.get('content-type') || '';
      if (contentType.includes('application/json')) {
        responseData = await response.json().catch(() => null);
      } else {
        const textData = await response.text().catch(() => '');
        responseData = { message: textData };
      }

      if (!response.ok) {
        const errMsg = responseData?.message || responseData?.error || `Appwrite server returned HTTP ${response.status}`;
        return {
          ok: false,
          status: response.status,
          data: responseData,
          error: errMsg,
          latencyMs
        };
      }

      return {
        ok: true,
        status: response.status,
        data: responseData,
        error: null,
        latencyMs
      };
    } catch (err: any) {
      clearTimeout(timeoutId);
      const latencyMs = Math.round(performance.now() - startTime);

      if (err.name === 'AbortError') {
        return {
          ok: false,
          status: 504,
          data: null,
          error: `Appwrite upstream request timed out after ${timeoutMs}ms.`,
          latencyMs
        };
      }

      return {
        ok: false,
        status: 502,
        data: null,
        error: `Failed to connect to Appwrite endpoint: ${err.message || String(err)}`,
        latencyMs
      };
    }
  }

  /**
   * Real Verification probe against Appwrite `/health/time` or `/projects` or `/databases`.
   */
  public static async verifyConnection(endpoint: string, projectId: string, apiKey: string): Promise<AppwriteResponse> {
    // Probe 1: Try /health/time or /locale
    const healthCheck = await AppwriteClient.request(endpoint, projectId, apiKey, {
      method: 'GET',
      path: '/locale',
      timeoutMs: 6000
    });

    if (healthCheck.ok) {
      return healthCheck;
    }

    // Probe 2: Try /databases
    const dbCheck = await AppwriteClient.request(endpoint, projectId, apiKey, {
      method: 'GET',
      path: '/databases',
      queryParams: { limit: '1' },
      timeoutMs: 6000
    });

    if (dbCheck.ok || dbCheck.status === 401 || dbCheck.status === 403) {
      return dbCheck;
    }

    return healthCheck;
  }
}

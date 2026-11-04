import test from 'node:test';
import assert from 'node:assert/strict';

// Client SSRF validator test replica
function validateEndpointUrl(endpoint) {
  let raw = (endpoint || '').trim();
  if (!raw) return { isValid: false, error: 'Endpoint URL is empty.' };

  if (!raw.startsWith('http://') && !raw.startsWith('https://')) {
    raw = `https://${raw}`;
  }

  try {
    const parsed = new URL(raw);
    const hostname = parsed.hostname.toLowerCase();

    if (
      hostname === 'localhost' ||
      hostname === '127.0.0.1' ||
      hostname === '0.0.0.0' ||
      hostname === '::1' ||
      hostname.endsWith('.local') ||
      hostname.endsWith('.internal')
    ) {
      return { isValid: false, error: 'SSRF Protection: Access to localhost or loopback is prohibited.' };
    }

    if (hostname.startsWith('169.254.') || hostname.startsWith('fe80:')) {
      return { isValid: false, error: 'SSRF Protection: Access to link-local or metadata services is prohibited.' };
    }

    const ipv4Match = hostname.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/);
    if (ipv4Match) {
      const [, p1, p2] = ipv4Match.map(Number);
      if (p1 === 10 || (p1 === 172 && p2 >= 16 && p2 <= 31) || (p1 === 192 && p2 === 168) || p1 === 127 || p1 === 0) {
        return { isValid: false, error: 'SSRF Protection: Access to private RFC 1918 network ranges is prohibited.' };
      }
    }

    let normalized = parsed.origin + parsed.pathname;
    if (normalized.endsWith('/')) {
      normalized = normalized.substring(0, normalized.length - 1);
    }

    return { isValid: true, normalizedUrl: normalized };
  } catch (e) {
    return { isValid: false, error: 'Invalid URL format.' };
  }
}

test('AppwriteClient - SSRF Protection blocks localhost and private IPs', () => {
  assert.equal(validateEndpointUrl('http://localhost:8080/v1').isValid, false);
  assert.equal(validateEndpointUrl('https://127.0.0.1/v1').isValid, false);
  assert.equal(validateEndpointUrl('https://10.0.0.1/v1').isValid, false);
  assert.equal(validateEndpointUrl('https://192.168.1.1/v1').isValid, false);
  assert.equal(validateEndpointUrl('https://169.254.169.254/latest/meta-data').isValid, false);
});

test('AppwriteClient - Normalizes valid Appwrite Cloud endpoints', () => {
  const result1 = validateEndpointUrl('https://cloud.appwrite.io/v1/');
  assert.equal(result1.isValid, true);
  assert.equal(result1.normalizedUrl, 'https://cloud.appwrite.io/v1');

  const result2 = validateEndpointUrl('sgp.cloud.appwrite.io/v1');
  assert.equal(result2.isValid, true);
  assert.equal(result2.normalizedUrl, 'https://sgp.cloud.appwrite.io/v1');
});

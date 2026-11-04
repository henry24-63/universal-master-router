export interface AccountRecord {
  id: string;
  label: string;
  endpoint: string;
  region: string;
  encrypted_api_key?: string;
  encryption_salt?: string;
  encryption_iv?: string;
  status: string;
}

export interface ProjectRecord {
  id: string;
  accountId: string;
  name: string;
  appwriteProjectId: string;
  region: string;
  mode: 'DEDICATED' | 'SHARED_POOL';
}

export interface AccountHealthRecord {
  accountId: string;
  status: 'HEALTHY' | 'WARNING' | 'CRITICAL';
  quotaUsagePercent?: number;
  latency_ms?: number;
}

export interface RoutingRuleRecord {
  id: string;
  tenant_id: string;
  strategy: 'DEDICATED' | 'SHARED_POOL' | 'HYBRID' | 'DIRECT' | 'ROUND_ROBIN' | 'LEAST_LATENCY' | 'FAILOVER';
  target_project_id?: string;
}

export interface RoutingDecision {
  success: boolean;
  selectedAccount?: AccountRecord;
  selectedProject?: ProjectRecord;
  strategyUsed: string;
  reason: string;
}

export class RoutingEngine {
  private accounts: AccountRecord[];
  private healthRecords: AccountHealthRecord[];
  private projects: ProjectRecord[];
  private rules: RoutingRuleRecord[];

  constructor(
    accounts: AccountRecord[] = [],
    healthRecords: AccountHealthRecord[] = [],
    projects: ProjectRecord[] = [],
    rules: RoutingRuleRecord[] = []
  ) {
    this.accounts = accounts;
    this.healthRecords = healthRecords;
    this.projects = projects;
    this.rules = rules;
  }

  /**
   * Instance method used by /v1/proxy/*
   */
  selectNode(params: {
    tenantId: string;
    operation: 'READ' | 'WRITE';
    targetProjectId?: string;
  }): RoutingDecision {
    const activeAccounts = this.accounts.filter(a => a.status === 'ACTIVE' || a.status === 'HEALTHY');
    if (activeAccounts.length === 0) {
      return {
        success: false,
        strategyUsed: 'NONE',
        reason: 'No active or healthy Appwrite accounts connected to the router.'
      };
    }

    const healthMap = new Map<string, AccountHealthRecord>();
    this.healthRecords.forEach(h => {
      const accId = h.accountId || (h as any).account_id;
      if (accId) healthMap.set(accId, h);
    });

    // Check for explicit routing rule for this tenant
    const rule = this.rules.find(r => r.tenant_id === params.tenantId || r.tenant_id === '*');
    const strategy = rule?.strategy || 'ROUND_ROBIN';
    const targetProjId = params.targetProjectId || rule?.target_project_id;

    // Filter accounts not in CRITICAL status
    const eligibleAccounts = activeAccounts.filter(a => {
      const h = healthMap.get(a.id);
      return !h || h.status !== 'CRITICAL';
    });

    const candidateAccounts = eligibleAccounts.length > 0 ? eligibleAccounts : activeAccounts;

    // Evaluate Strategy
    let selectedAccount: AccountRecord;
    let reason = '';

    if (strategy === 'LEAST_LATENCY') {
      selectedAccount = [...candidateAccounts].sort((a, b) => {
        const latA = healthMap.get(a.id)?.latency_ms ?? 9999;
        const latB = healthMap.get(b.id)?.latency_ms ?? 9999;
        return latA - latB;
      })[0];
      reason = `Selected account ${selectedAccount.label} based on lowest latency (${healthMap.get(selectedAccount.id)?.latency_ms ?? 'unknown'} ms).`;
    } else if (strategy === 'FAILOVER') {
      selectedAccount = candidateAccounts[0];
      reason = `Selected primary failover account ${selectedAccount.label}.`;
    } else if (strategy === 'DIRECT' || strategy === 'DEDICATED') {
      if (targetProjId) {
        const proj = this.projects.find(p => p.id === targetProjId || p.appwriteProjectId === targetProjId);
        if (proj) {
          const acc = candidateAccounts.find(a => a.id === proj.accountId);
          if (acc) {
            selectedAccount = acc;
            reason = `Directly routed to configured project ${proj.name} on account ${acc.label}.`;
          } else {
            selectedAccount = candidateAccounts[0];
            reason = `Configured account for ${proj.name} unhealthy; fell back to ${selectedAccount.label}.`;
          }
        } else {
          selectedAccount = candidateAccounts[0];
          reason = `Configured project ID ${targetProjId} not found; fell back to ${selectedAccount.label}.`;
        }
      } else {
        selectedAccount = candidateAccounts[0];
        reason = `Selected account ${selectedAccount.label} for dedicated strategy.`;
      }
    } else {
      // SHARED_POOL / ROUND_ROBIN / HYBRID
      let hash = 0;
      for (let i = 0; i < params.tenantId.length; i++) {
        hash = (hash << 5) - hash + params.tenantId.charCodeAt(i);
        hash |= 0;
      }
      const index = Math.abs(hash) % candidateAccounts.length;
      selectedAccount = candidateAccounts[index];
      reason = `Deterministic hash pool index ${index} selected account ${selectedAccount.label} for tenant ${params.tenantId}.`;
    }

    const matchedProject = this.projects.find(p => p.accountId === selectedAccount.id) || {
      id: selectedAccount.id,
      accountId: selectedAccount.id,
      name: selectedAccount.label,
      appwriteProjectId: '6aeb5720003291868b64',
      region: selectedAccount.region,
      mode: 'SHARED_POOL' as const
    };

    return {
      success: true,
      selectedAccount,
      selectedProject: matchedProject,
      strategyUsed: strategy,
      reason
    };
  }

  /**
   * Deterministically resolves a write destination for a given tenant (Static API).
   * Strict invariant: Resolves to EXACTLY ONE eligible project node. Never fans out.
   */
  static selectWriteDestination(
    tenantId: string,
    strategy: 'DEDICATED' | 'SHARED_POOL' | 'HYBRID' | 'DIRECT' | 'ROUND_ROBIN' | 'LEAST_LATENCY' | 'FAILOVER',
    availableProjects: ProjectRecord[],
    accountHealthMap: Map<string, AccountHealthRecord>,
    configuredTargetProjectId?: string
  ): { selectedProject: ProjectRecord; strategyUsed: string; reason: string } {
    if (!availableProjects || availableProjects.length === 0) {
      throw new Error('Routing error: No connected projects available in the router.');
    }

    if (configuredTargetProjectId) {
      const target = availableProjects.find(p => p.id === configuredTargetProjectId);
      if (target) {
        const health = accountHealthMap.get(target.accountId);
        if (health && health.status === 'CRITICAL') {
          throw new Error(`Write blocked: Target dedicated project ${target.name} has exhausted account quota.`);
        }
        return {
          selectedProject: target,
          strategyUsed: strategy,
          reason: `Routed to configured project ${target.name} for tenant ${tenantId}.`
        };
      }
    }

    const healthyProjects = availableProjects.filter(p => {
      const health = accountHealthMap.get(p.accountId);
      return !health || health.status !== 'CRITICAL';
    });

    if (healthyProjects.length === 0) {
      throw new Error('Routing failure: All available projects are currently quota-exhausted or in critical health.');
    }

    if (strategy === 'DEDICATED' || strategy === 'DIRECT') {
      const dedicatedProject = healthyProjects.find(p => p.mode === 'DEDICATED') || healthyProjects[0];
      return {
        selectedProject: dedicatedProject,
        strategyUsed: strategy,
        reason: `Allocated dedicated project ${dedicatedProject.name}.`
      };
    } else {
      const poolProjects = healthyProjects.filter(p => p.mode === 'SHARED_POOL');
      const candidateList = poolProjects.length > 0 ? poolProjects : healthyProjects;

      let hash = 0;
      for (let i = 0; i < tenantId.length; i++) {
        hash = (hash << 5) - hash + tenantId.charCodeAt(i);
        hash |= 0;
      }
      const index = Math.abs(hash) % candidateList.length;
      const selected = candidateList[index];

      return {
        selectedProject: selected,
        strategyUsed: strategy,
        reason: `Deterministic pool node ${selected.name} allocated for tenant ${tenantId}.`
      };
    }
  }

  /**
   * Resolves read destination using known resource ownership mapping (Static API).
   */
  static selectReadSource(
    projectId: string,
    availableProjects: ProjectRecord[],
    accountHealthMap: Map<string, AccountHealthRecord>
  ): ProjectRecord {
    const project = availableProjects.find(p => p.id === projectId);
    if (!project) {
      throw new Error(`Read error: Project ${projectId} is not connected to this Master Router.`);
    }

    const health = accountHealthMap.get(project.accountId);
    if (health && health.status === 'CRITICAL') {
      throw new Error(`Read unavailable: Account for project ${project.name} is currently suspended or read-limited.`);
    }

    return project;
  }
}

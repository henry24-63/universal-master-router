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
  quotaUsagePercent: number;
}

export interface RoutingDecision {
  selectedProject: ProjectRecord;
  strategyUsed: 'DEDICATED' | 'SHARED_POOL' | 'HYBRID';
  reason: string;
}

export class RoutingEngine {
  /**
   * Deterministically resolves a write destination for a given tenant.
   * Strict invariant: Resolves to EXACTLY ONE eligible project node. Never fans out.
   */
  static selectWriteDestination(
    tenantId: string,
    strategy: 'DEDICATED' | 'SHARED_POOL' | 'HYBRID',
    availableProjects: ProjectRecord[],
    accountHealthMap: Map<string, AccountHealthRecord>,
    configuredTargetProjectId?: string
  ): RoutingDecision {
    if (!availableProjects || availableProjects.length === 0) {
      throw new Error('Routing error: No connected projects available in the router.');
    }

    // 1. If explicit dedicated rule exists, check eligibility
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

    // 2. Filter healthy eligible projects
    const healthyProjects = availableProjects.filter(p => {
      const health = accountHealthMap.get(p.accountId);
      return !health || health.status !== 'CRITICAL';
    });

    if (healthyProjects.length === 0) {
      throw new Error('Routing failure: All available projects are currently quota-exhausted or in critical health.');
    }

    // 3. Strategy evaluation
    if (strategy === 'DEDICATED') {
      const dedicatedProject = healthyProjects.find(p => p.mode === 'DEDICATED') || healthyProjects[0];
      return {
        selectedProject: dedicatedProject,
        strategyUsed: 'DEDICATED',
        reason: `Allocated dedicated project ${dedicatedProject.name}.`
      };
    } else {
      // Shared Pool or Hybrid: pick least-utilized or stable hash based on tenantId
      const poolProjects = healthyProjects.filter(p => p.mode === 'SHARED_POOL');
      const candidateList = poolProjects.length > 0 ? poolProjects : healthyProjects;

      // Deterministic round-robin/hash selection based on tenantId string
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
   * Resolves read destination using known resource ownership mapping.
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

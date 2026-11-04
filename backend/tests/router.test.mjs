import test from 'node:test';
import assert from 'node:assert/strict';

class RoutingEngine {
  static selectWriteDestination(
    tenantId,
    strategy,
    availableProjects,
    accountHealthMap,
    configuredTargetProjectId
  ) {
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

    if (strategy === 'DEDICATED') {
      const dedicatedProject = healthyProjects.find(p => p.mode === 'DEDICATED') || healthyProjects[0];
      return {
        selectedProject: dedicatedProject,
        strategyUsed: 'DEDICATED',
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

  static selectReadSource(
    projectId,
    availableProjects,
    accountHealthMap
  ) {
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

const mockProjects = [
  { id: 'proj_sgp_dedicated', accountId: 'acc_sgp', name: 'SGP Dedicated Node', mode: 'DEDICATED' },
  { id: 'proj_fra_pool1', accountId: 'acc_fra', name: 'FRA Pool Node 1', mode: 'SHARED_POOL' },
  { id: 'proj_iad_pool2', accountId: 'acc_iad', name: 'IAD Pool Node 2', mode: 'SHARED_POOL' }
];

test('RoutingEngine - Dedicated strategy routes to configured dedicated node', () => {
  const healthMap = new Map();
  healthMap.set('acc_sgp', { accountId: 'acc_sgp', status: 'HEALTHY' });

  const decision = RoutingEngine.selectWriteDestination(
    'tenant_enterprise_1',
    'DEDICATED',
    mockProjects,
    healthMap,
    'proj_sgp_dedicated'
  );

  assert.equal(decision.selectedProject.id, 'proj_sgp_dedicated');
  assert.equal(decision.strategyUsed, 'DEDICATED');
});

test('RoutingEngine - Shared pool routes deterministically for the same tenant', () => {
  const healthMap = new Map();
  const decision1 = RoutingEngine.selectWriteDestination(
    'tenant_fintech_alpha',
    'SHARED_POOL',
    mockProjects,
    healthMap
  );
  const decision2 = RoutingEngine.selectWriteDestination(
    'tenant_fintech_alpha',
    'SHARED_POOL',
    mockProjects,
    healthMap
  );

  assert.equal(decision1.selectedProject.id, decision2.selectedProject.id);
  assert.equal(decision1.selectedProject.mode, 'SHARED_POOL');
});

test('RoutingEngine - Single-destination write invariant (Never fans out)', () => {
  const healthMap = new Map();
  const decision = RoutingEngine.selectWriteDestination(
    'tenant_test',
    'SHARED_POOL',
    mockProjects,
    healthMap
  );

  assert.ok(decision.selectedProject);
  assert.equal(typeof decision.selectedProject.id, 'string');
});

test('RoutingEngine - Quota exhaustion blocks write to critical dedicated node', () => {
  const healthMap = new Map();
  healthMap.set('acc_sgp', { accountId: 'acc_sgp', status: 'CRITICAL', quotaUsagePercent: 100 });

  assert.throws(
    () => RoutingEngine.selectWriteDestination(
      'tenant_enterprise_1',
      'DEDICATED',
      mockProjects,
      healthMap,
      'proj_sgp_dedicated'
    ),
    /exhausted account quota/
  );
});

test('RoutingEngine - Read routing succeeds for healthy project', () => {
  const healthMap = new Map();
  healthMap.set('acc_fra', { accountId: 'acc_fra', status: 'HEALTHY' });

  const project = RoutingEngine.selectReadSource('proj_fra_pool1', mockProjects, healthMap);
  assert.equal(project.id, 'proj_fra_pool1');
});

test('RoutingEngine - Read routing blocked if target account is suspended or critical', () => {
  const healthMap = new Map();
  healthMap.set('acc_fra', { accountId: 'acc_fra', status: 'CRITICAL' });

  assert.throws(
    () => RoutingEngine.selectReadSource('proj_fra_pool1', mockProjects, healthMap),
    /suspended or read-limited/
  );
});

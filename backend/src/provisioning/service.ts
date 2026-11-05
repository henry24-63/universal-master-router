import { ProvisioningJob, ProvisioningResource, ProvisioningTemplate } from './models.ts';

export interface ProvisioningService {
  previewProvisioning(
    accountId: string,
    templateId: string,
    databaseId?: string
  ): Promise<any>;

  validateProvisioning(
    accountId: string,
    templateId: string
  ): Promise<boolean>;

  createProvisioningJob(
    accountId: string,
    projectId: string,
    templateId: string
  ): Promise<ProvisioningJob>;

  getProvisioningJob(jobId: string): Promise<ProvisioningJob | null>;

  saveResourceMapping(resource: ProvisioningResource): Promise<void>;

  listProvisioningResources(jobId: string): Promise<ProvisioningResource[]>;
}

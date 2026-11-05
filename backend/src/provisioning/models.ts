export type ProvisioningStatus = 'PENDING' | 'VALIDATING' | 'RUNNING' | 'COMPLETED' | 'PARTIAL' | 'FAILED' | 'CANCELLED';
export type ResourceType = 'DATABASE' | 'TABLE' | 'COLUMN' | 'INDEX' | 'LEGACY_COLLECTION';

export interface ProvisioningJob {
  id: string;
  connected_account_id: string;
  appwrite_project_id: string;
  template_id: string;
  template_version: string;
  status: ProvisioningStatus;
  result_data?: any;
  error_message?: string;
  created_at: string;
  updated_at: string;
}

export interface ProvisioningResource {
  id: string;
  job_id: string;
  resource_type: ResourceType;
  resource_name: string;
  appwrite_id?: string;
  status: ProvisioningStatus;
  error_message?: string;
}

export interface ProvisioningTemplate {
  template_id: string;
  version: string;
  display_name: string;
  description: string;
  database_config: {
    name: string;
  };
  tables: Array<{
    name: string;
    columns: Array<{
      key: string;
      type: string;
      size?: number;
      default?: any;
      required: boolean;
      array?: boolean;
    }>;
    indexes: Array<{
      key: string;
      type: 'key' | 'unique' | 'fulltext';
      columns: string[];
    }>;
  }>;
}

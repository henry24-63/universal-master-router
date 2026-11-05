import * as Models from './models.ts';

export const UNIVERSAL_STARTER: Models.ProvisioningTemplate = {
  template_id: 'UNIVERSAL_STARTER',
  version: '1.0.0',
  display_name: 'Universal Starter',
  description: 'Basic structure for user management and activity logging.',
  database_config: { name: 'universal_starter_db' },
  tables: [
    {
      name: 'users',
      columns: [
        { key: 'email', type: 'string', required: true },
        { key: 'name', type: 'string', required: false },
        { key: 'created_at', type: 'datetime', required: true }
      ],
      indexes: [{ key: 'email_idx', type: 'unique', columns: ['email'] }]
    },
    {
      name: 'api_records',
      columns: [
        { key: 'method', type: 'string', required: true },
        { key: 'path', type: 'string', required: true },
        { key: 'status', type: 'integer', required: true }
      ],
      indexes: []
    }
  ]
};

export const SAAS_STARTER: Models.ProvisioningTemplate = {
  template_id: 'SAAS_STARTER',
  version: '1.0.0',
  display_name: 'SaaS Starter',
  description: 'Comprehensive setup for SaaS applications.',
  database_config: { name: 'saas_starter_db' },
  tables: [
    {
      name: 'organizations',
      columns: [
        { key: 'name', type: 'string', required: true },
        { key: 'billing_email', type: 'string', required: true }
      ],
      indexes: [{ key: 'org_name_idx', type: 'key', columns: ['name'] }]
    }
  ]
};

export const TemplateRegistry: Record<string, Models.ProvisioningTemplate> = {
  [UNIVERSAL_STARTER.template_id]: UNIVERSAL_STARTER,
  [SAAS_STARTER.template_id]: SAAS_STARTER
};

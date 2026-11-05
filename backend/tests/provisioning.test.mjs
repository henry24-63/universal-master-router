import test from 'node:test';
import assert from 'node:assert/strict';
import { TemplateRegistry } from '../src/provisioning/templates.ts';

test('Provisioning - Template Registry has required templates', async () => {
  assert.ok(TemplateRegistry['UNIVERSAL_STARTER'], 'Missing UNIVERSAL_STARTER');
  assert.ok(TemplateRegistry['SAAS_STARTER'], 'Missing SAAS_STARTER');
});

test('Provisioning - Template Schema is valid', async () => {
  const template = TemplateRegistry['UNIVERSAL_STARTER'];
  assert.equal(template.tables.length, 2);
  assert.equal(template.tables[0].name, 'users');
  assert.equal(template.tables[0].columns.length, 3);
});

import test from 'node:test';
import assert from 'node:assert/strict';
import { renderDashboardHtml } from '../src/ui/dashboard.ts';

test('Dashboard UI - Renders essential HTML sections', async () => {
  const html = renderDashboardHtml();
  assert.ok(html.includes('Universal Master Router'), 'Missing title');
  assert.ok(html.includes('id="main-view"'), 'Missing main view container');
  assert.ok(html.includes('id="dashboard-view"'), 'Missing dashboard view container');
  assert.ok(html.includes('id="login-modal"'), 'Missing login modal');
});

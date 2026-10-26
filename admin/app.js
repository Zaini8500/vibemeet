async function call(path) {
  const base = document.getElementById('base').value.replace(/\/$/, '');
  const token = document.getElementById('token').value;
  const role = document.getElementById('role').value;
  const r = await fetch(base + path, { headers: { Authorization: 'Bearer ' + token, 'x-admin-role': role } });
  document.getElementById('out').textContent = JSON.stringify(await r.json(), null, 2);
}
function loadReports() { return call('/admin/reports'); }
function loadAnalytics() { return call('/admin/analytics'); }

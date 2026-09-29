import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { auditApi } from '../../lib/api';
import { formatDateTime } from '../../lib/format';
import { Badge, ErrorState, Field, Input, LoadingState, Pagination } from '../../components/ui';

const CATEGORIES = [
  'USER', 'PRODUCT', 'CATEGORY', 'VARIANT', 'INVENTORY', 'PROMOTION', 'REVIEW',
  'ORDER', 'PAYMENT', 'REFUND', 'AUTH', 'AUDIT',
];

export default function AdminAuditPage() {
  const [category, setCategory] = useState('');
  const [action, setAction] = useState('');
  const [resourceType, setResourceType] = useState('');
  const [resourceId, setResourceId] = useState('');
  const [actorUserId, setActorUserId] = useState('');
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: ['admin', 'audit', { category, action, resourceType, resourceId, actorUserId, page }],
    queryFn: () =>
      auditApi.list({
        category: category || undefined,
        action: action || undefined,
        resourceType: resourceType || undefined,
        resourceId: resourceId || undefined,
        actorUserId: actorUserId || undefined,
        page,
        size: 25,
      }),
  });

  if (query.isPending) return <LoadingState label="Loading audit log…" />;
  if (query.isError) return <ErrorState error={query.error} />;

  return (
    <div>
      <h2 className="admin-main__title">Audit log</h2>
      <p className="text-sm text-muted mb-4">Append-only record of administrative and sensitive actions. Read-only.</p>
      <div className="filters-bar">
        <Field label="Action">
          <Input value={action} onChange={(e) => { setAction(e.target.value); setPage(0); }} placeholder="e.g. PRODUCT_UPDATED" />
        </Field>
        <Field label="Category">
          <select className="select" value={category} onChange={(e) => { setCategory(e.target.value); setPage(0); }}>
            <option value="">All</option>
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
        </Field>
        <Field label="Resource type">
          <Input value={resourceType} onChange={(e) => { setResourceType(e.target.value); setPage(0); }} placeholder="PRODUCT / ORDER / USER" />
        </Field>
        <Field label="Resource ID">
          <Input value={resourceId} onChange={(e) => { setResourceId(e.target.value); setPage(0); }} />
        </Field>
        <Field label="Actor user ID">
          <Input value={actorUserId} onChange={(e) => { setActorUserId(e.target.value); setPage(0); }} />
        </Field>
      </div>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>When</th>
              <th>Actor</th>
              <th>Action</th>
              <th>Category</th>
              <th>Resource</th>
              <th>Details</th>
            </tr>
          </thead>
          <tbody>
            {query.data.content.map((log) => (
              <tr key={log.id}>
                <td className="text-xs">{formatDateTime(log.occurredAt)}</td>
                <td className="text-xs">{log.actorUserId ? log.actorUserId.slice(0, 8) : 'system'}</td>
                <td><Badge tone="neutral">{log.action}</Badge></td>
                <td>{log.category}</td>
                <td className="text-xs">
                  {log.resourceType}
                  {log.resourceId ? ` · ${log.resourceId.slice(0, 8)}` : ''}
                </td>
                <td className="text-xs text-muted" style={{ maxWidth: 320, wordBreak: 'break-word' }}>
                  {log.details ?? '—'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination page={page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} size={query.data.size} onChange={setPage} />
    </div>
  );
}
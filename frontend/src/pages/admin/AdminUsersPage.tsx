import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminUsersApi } from '../../lib/api';
import { formatDateTime, humanize } from '../../lib/format';
import { useAuth } from '../../lib/auth';
import { Badge, Button, ErrorState, Field, Input, LoadingState, Modal, Pagination, Select, useToasts, errorMessage } from '../../components/ui';
import type { UserRole } from '../../lib/types';

export default function AdminUsersPage() {
  const queryClient = useQueryClient();
  const toasts = useToasts();
  const { profile } = useAuth();
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<UserRole | ''>('');
  const [status, setStatus] = useState<string>('');
  const [page, setPage] = useState(0);
  const [roleFor, setRoleFor] = useState<{ id: string; role: UserRole } | null>(null);

  const query = useQuery({
    queryKey: ['admin', 'users', email, role, status, page],
    queryFn: () =>
      adminUsersApi.list({ email: email || undefined, role: role || undefined, status: status || undefined, page, size: 15 }),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
  };

  const act = useMutation({
    mutationFn: ({ id, op }: { id: string; op: 'lock' | 'unlock' | 'activate' | 'deactivate' }) => {
      const fn = {
        lock: adminUsersApi.lock,
        unlock: adminUsersApi.unlock,
        activate: adminUsersApi.activate,
        deactivate: adminUsersApi.deactivate,
      }[op];
      return fn(id);
    },
    onSuccess: () => {
      invalidate();
      toasts.push('success', 'User updated');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  const changeRole = useMutation({
    mutationFn: () => {
      if (!roleFor) return Promise.reject(new Error('no target'));
      return adminUsersApi.changeRole(roleFor.id, roleFor.role);
    },
    onSuccess: () => {
      invalidate();
      setRoleFor(null);
      toasts.push('success', 'Role changed — their sessions were revoked');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  if (query.isPending) return <LoadingState label="Loading users…" />;
  if (query.isError) return <ErrorState error={query.error} />;

  return (
    <div>
      <h2 className="admin-main__title">User management</h2>
      <div className="filters-bar">
        <Field label="Email">
          <Input value={email} onChange={(e) => { setEmail(e.target.value); setPage(0); }} />
        </Field>
        <Field label="Role">
          <Select value={role} onChange={(e) => { setRole(e.target.value as UserRole | ''); setPage(0); }}>
            <option value="">All roles</option>
            <option value="CUSTOMER">Customer</option>
            <option value="STAFF">Staff</option>
            <option value="ADMIN">Admin</option>
          </Select>
        </Field>
        <Field label="Status">
          <Select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
            <option value="">All statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="LOCKED">Locked</option>
            <option value="DEACTIVATED">Deactivated</option>
            <option value="PENDING_VERIFICATION">Pending verification</option>
          </Select>
        </Field>
      </div>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>User</th>
              <th>Role</th>
              <th>Status</th>
              <th>Verified</th>
              <th>Joined</th>
              <th className="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {query.data.content.map((u) => {
              const self = profile?.id === u.id;
              return (
                <tr key={u.id}>
                  <td>
                    <strong>{u.fullName || u.email}</strong>
                    <div className="text-xs text-faint">{u.email}</div>
                    {self && <Badge tone="accent">You</Badge>}
                  </td>
                  <td><Badge tone={u.role === 'ADMIN' ? 'accent' : u.role === 'STAFF' ? 'info' : 'neutral'}>{u.role}</Badge></td>
                  <td><Badge tone={u.accountStatus === 'ACTIVE' ? 'success' : u.accountStatus === 'LOCKED' ? 'warning' : u.accountStatus === 'DEACTIVATED' ? 'error' : 'neutral'}>{humanize(u.accountStatus)}</Badge></td>
                  <td>{u.emailVerified ? 'Yes' : 'No'}</td>
                  <td className="text-xs text-muted">{formatDateTime(u.createdAt)}</td>
                  <td>
                    <div className="actions justify-end">
                      {!self && (
                        <>
                          {u.accountStatus === 'ACTIVE' || u.accountStatus === 'PENDING_VERIFICATION' ? (
                            <Button variant="outline" size="sm" onClick={() => act.mutate({ id: u.id, op: 'deactivate' })}>
                              Deactivate
                            </Button>
                          ) : u.accountStatus === 'DEACTIVATED' ? (
                            <Button variant="dark" size="sm" onClick={() => act.mutate({ id: u.id, op: 'activate' })}>
                              Activate
                            </Button>
                          ) : null}
                          {u.accountStatus === 'LOCKED' ? (
                            <Button variant="dark" size="sm" onClick={() => act.mutate({ id: u.id, op: 'unlock' })}>
                              Unlock
                            </Button>
                          ) : (
                            <Button variant="outline" size="sm" onClick={() => act.mutate({ id: u.id, op: 'lock' })}>
                              Lock
                            </Button>
                          )}
                          <Button variant="outline" size="sm" onClick={() => setRoleFor({ id: u.id, role: u.role })}>
                            Change role
                          </Button>
                        </>
                      )}
                      {self && <span className="text-xs text-faint">Self-protection</span>}
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <Pagination page={page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} size={query.data.size} onChange={setPage} />

      <Modal open={Boolean(roleFor)} title="Change role" onClose={() => setRoleFor(null)}>
        {roleFor && (
          <div>
            <p className="text-sm text-muted mb-4">
              Changing the role revokes the user's active sessions (refresh tokens) immediately.
            </p>
            <Field label="New role">
              <Select value={roleFor.role} onChange={(e) => setRoleFor({ ...roleFor, role: e.target.value as UserRole })}>
                <option value="CUSTOMER">Customer</option>
                <option value="STAFF">Staff</option>
                <option value="ADMIN">Admin</option>
              </Select>
            </Field>
            <div className="flex gap-3">
              <Button onClick={() => changeRole.mutate()} loading={changeRole.isPending}>
                Change role
              </Button>
              <Button variant="ghost" onClick={() => setRoleFor(null)}>Cancel</Button>
            </div>
          </div>
        )}
      </Modal>
      {toasts.node}
    </div>
  );
}
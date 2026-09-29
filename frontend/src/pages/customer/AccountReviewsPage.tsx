import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { reviewsApi } from '../../lib/api';
import { formatDate, humanize } from '../../lib/format';
import { Badge, Button, EmptyState, ErrorState, Field, Input, LoadingState, Modal, Pagination, Stars, useToasts, errorMessage } from '../../components/ui';
import type { ReviewRequest } from '../../lib/types';

export default function AccountReviewsPage() {
  const queryClient = useQueryClient();
  const toasts = useToasts();
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<{ id: string; form: ReviewRequest } | null>(null);

  const query = useQuery({ queryKey: ['reviews', 'mine', page], queryFn: () => reviewsApi.mine(page, 10) });
  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['reviews'] });
  };

  const save = useMutation({
    mutationFn: () => {
      if (!editing) return Promise.reject(new Error('no form'));
      return reviewsApi.update(editing.id, editing.form);
    },
    onSuccess: () => {
      invalidate();
      setEditing(null);
      toasts.push('success', 'Review updated — pending moderation again');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  const remove = useMutation({
    mutationFn: (id: string) => reviewsApi.remove(id),
    onSuccess: () => {
      invalidate();
      toasts.push('success', 'Review deleted');
    },
    onError: (e) => toasts.push('error', errorMessage(e)),
  });

  if (query.isPending) return <LoadingState label="Loading reviews…" />;
  if (query.isError) return <ErrorState error={query.error} />;
  if (query.data.content.length === 0) {
    return (
      <EmptyState
        title="No reviews yet"
        body="Reviews can only be written for orders that have been delivered."
        action={
          <Link className="btn btn--dark" to="/account/orders">
            View your orders
          </Link>
        }
      />
    );
  }

  return (
    <div>
      <h2>My reviews</h2>
      <div className="card">
        {query.data.content.map((r) => (
          <div className="review-card" key={r.id}>
            <div className="review-card__head">
              <div className="flex gap-2 items-center">
                <Stars rating={r.rating} />
                <Link to={`/products/${r.productSlug}`} className="review-card__author">
                  {r.productName}
                </Link>
              </div>
              <Badge tone={r.status === 'APPROVED' ? 'success' : r.status === 'REJECTED' ? 'error' : 'warning'}>
                {humanize(r.status)}
              </Badge>
            </div>
            {r.title && <div className="review-card__title">{r.title}</div>}
            {r.body && <div className="review-card__body">{r.body}</div>}
            <div className="text-xs text-faint mt-2">{formatDate(r.createdAt)}</div>
            <div className="flex gap-2 mt-3">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setEditing({ id: r.id, form: { rating: r.rating, title: r.title ?? '', body: r.body ?? '' } })}
              >
                Edit
              </Button>
              <Button variant="danger" size="sm" onClick={() => remove.mutate(r.id)}>
                Delete
              </Button>
            </div>
          </div>
        ))}
      </div>
      <Pagination page={page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} size={query.data.size} onChange={setPage} />

      <Modal open={Boolean(editing)} title="Edit review" onClose={() => setEditing(null)}>
        {editing && (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              save.mutate();
            }}
          >
            <Field label="Rating">
              <select
                className="select"
                value={editing.form.rating}
                onChange={(e) => setEditing({ ...editing, form: { ...editing.form, rating: Number(e.target.value) } })}
              >
                {[5, 4, 3, 2, 1].map((n) => (
                  <option key={n} value={n}>
                    {n} star{n > 1 ? 's' : ''}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Title">
              <Input value={editing.form.title ?? ''} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, title: e.target.value } })} />
            </Field>
            <Field label="Review">
              <textarea
                className="textarea"
                value={editing.form.body ?? ''}
                onChange={(e) => setEditing({ ...editing, form: { ...editing.form, body: e.target.value } })}
              />
            </Field>
            <p className="text-xs text-muted mb-4">Edits return the review to pending moderation.</p>
            <div className="flex gap-3">
              <Button type="submit" loading={save.isPending}>Save</Button>
              <Button variant="ghost" onClick={() => setEditing(null)}>Cancel</Button>
            </div>
          </form>
        )}
      </Modal>
      {toasts.node}
    </div>
  );
}
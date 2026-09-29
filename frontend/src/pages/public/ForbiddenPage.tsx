import { Link } from 'react-router-dom';

export default function ForbiddenPage() {
  return (
    <div className="container container--narrow" style={{ paddingBlock: '4rem', textAlign: 'center' }}>
      <h1 style={{ fontSize: '5rem', marginBottom: 0 }}>403</h1>
      <p className="text-muted mb-6">You do not have permission to view this page.</p>
      <Link className="btn btn--dark" to="/">
        Back to the storefront
      </Link>
    </div>
  );
}
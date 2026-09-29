import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <div className="container container--narrow" style={{ paddingBlock: '4rem', textAlign: 'center' }}>
      <h1 style={{ fontSize: '5rem', marginBottom: 0 }}>404</h1>
      <p className="text-muted mb-6">The page you are looking for does not exist.</p>
      <Link className="btn btn--dark" to="/">
        Back to the storefront
      </Link>
    </div>
  );
}
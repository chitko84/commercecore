import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import type { ProfileResponse, UserRole } from './types';
import { accountApi, authApi, clearTokens, hasTokens, storeTokens } from './api';
import type { LoginRequest, RegisterRequest } from './types';

type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

interface AuthContextValue {
  status: AuthStatus;
  profile: ProfileResponse | null;
  login: (body: LoginRequest) => Promise<ProfileResponse>;
  register: (body: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
  refreshProfile: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [profile, setProfile] = useState<ProfileResponse | null>(null);

  const loadProfile = async () => {
    if (!hasTokens()) {
      setStatus('anonymous');
      setProfile(null);
      return;
    }
    try {
      const p = await accountApi.profile();
      setProfile(p);
      setStatus('authenticated');
    } catch {
      // Session could not be restored (expired refresh token).
      clearTokens();
      setProfile(null);
      setStatus('anonymous');
    }
  };

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const p = await loadProfile();
      if (!cancelled) void p;
    })();
    const onSessionExpired = () => {
      setProfile(null);
      setStatus('anonymous');
    };
    window.addEventListener('commercecore:session-expired', onSessionExpired);
    return () => {
      cancelled = true;
      window.removeEventListener('commercecore:session-expired', onSessionExpired);
    };
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      profile,
      async login(body) {
        const tokens = await authApi.login(body);
        storeTokens(tokens);
        const p = await accountApi.profile();
        setProfile(p);
        setStatus('authenticated');
        return p;
      },
      async register(body) {
        await authApi.register(body);
      },
      async logout() {
        await authApi.logout();
        setProfile(null);
        setStatus('anonymous');
      },
      async refreshProfile() {
        await loadProfile();
      },
    }),
    [status, profile],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}

export function useRole(): UserRole | null {
  return useAuth().profile?.role ?? null;
}

export function isStaff(role?: UserRole | null): boolean {
  return role === 'STAFF' || role === 'ADMIN';
}

export function isAdmin(role?: UserRole | null): boolean {
  return role === 'ADMIN';
}

function RoleGuard({
  roles,
  children,
  fallback,
}: {
  roles: UserRole[];
  children: ReactNode;
  fallback: ReactNode;
}) {
  const { status, profile } = useAuth();
  const location = useLocation();
  if (status === 'loading') return <FullPageLoader />;
  if (status === 'anonymous' || !profile) {
    const from = `${location.pathname}${location.search}${location.hash}`;
    return <Navigate to="/login" replace state={{ from }} />;
  }
  if (!roles.includes(profile.role)) return <>{fallback}</>;
  return <>{children}</>;
}

export function RequireAuth({ children }: { children: ReactNode }) {
  return (
    <RoleGuard roles={['CUSTOMER', 'STAFF', 'ADMIN']} fallback={<ForbiddenPage />}>
      {children}
    </RoleGuard>
  );
}

export function RequireCustomer({ children }: { children: ReactNode }) {
  return (
    <RoleGuard roles={['CUSTOMER']} fallback={<ForbiddenPage />}>
      {children}
    </RoleGuard>
  );
}

export function RequireStaff({ children }: { children: ReactNode }) {
  return (
    <RoleGuard roles={['STAFF', 'ADMIN']} fallback={<ForbiddenPage />}>
      {children}
    </RoleGuard>
  );
}

export function RequireAdmin({ children }: { children: ReactNode }) {
  return (
    <RoleGuard roles={['ADMIN']} fallback={<ForbiddenPage />}>
      {children}
    </RoleGuard>
  );
}

function FullPageLoader() {
  return (
    <div className="loading-state" role="status" aria-live="polite">
      <div className="skeleton skeleton--title" />
      <p className="text-muted">Loading your session…</p>
    </div>
  );
}

function ForbiddenPage() {
  return (
    <div className="error-state">
      <h3>Access denied</h3>
      <p>Your account does not have permission to view this area.</p>
      <a className="btn btn--dark" href="/">
        Back to storefront
      </a>
    </div>
  );
}

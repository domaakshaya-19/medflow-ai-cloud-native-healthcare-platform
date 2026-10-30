import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { usePatientProfile } from '../context/PatientProfileContext';
import ErrorAlert from './ui/ErrorAlert';
import LoadingState from './ui/LoadingState';

export default function PatientProfileGuard({ children }) {
  const { user } = useAuth();
  const { status, error, refresh } = usePatientProfile();
  if (user.role !== 'PATIENT') return children;
  if (status === 'idle' || status === 'loading') return <LoadingState label="Checking your Patient Profile…" />;
  if (status === 'missing') return <Navigate to="/patient/setup" replace />;
  if (status === 'error') return <section className="onboarding-error"><h1>We couldn’t verify your Patient Profile</h1>
    <p>You have not been redirected to setup because the service returned an error rather than a confirmed missing profile.</p>
    <ErrorAlert message={error} onRetry={refresh} /></section>;
  return children;
}

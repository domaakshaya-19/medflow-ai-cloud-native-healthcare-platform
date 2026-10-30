import { Navigate, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { apiError } from '../../api';
import { patientsApi } from '../../api/patients';
import PatientProfileForm from '../../components/PatientProfileForm';
import ErrorAlert from '../../components/ui/ErrorAlert';
import LoadingState from '../../components/ui/LoadingState';
import { useAuth } from '../../context/AuthContext';
import { usePatientProfile } from '../../context/PatientProfileContext';

export default function PatientProfileSetup() {
  const { user } = useAuth(); const { status, error, refresh, setProfile } = usePatientProfile(); const navigate = useNavigate();
  const [saving, setSaving] = useState(false); const [submitError, setSubmitError] = useState(''); const [fields, setFields] = useState({});
  if (user.role !== 'PATIENT') return <Navigate to="/dashboard" replace />;
  if (status === 'idle' || status === 'loading') return <LoadingState label="Checking your Patient Profile…" />;
  if (status === 'ready') return <Navigate to="/patients" replace />;
  if (status === 'error') return <section className="onboarding-card"><p className="eyebrow">Profile verification</p><h1>Patient Profile setup is temporarily unavailable</h1>
    <p>The service did not confirm that your profile is missing, so MedFlow will not create a duplicate.</p><ErrorAlert message={error} onRetry={refresh} /></section>;
  async function create(profile) { setSaving(true); setSubmitError(''); setFields({}); try { const created = await patientsApi.create(profile); setProfile(created); navigate('/patients', { replace: true, state: { created: true } }); }
    catch (requestError) { const normalized = apiError(requestError, 'Your Patient Profile could not be created.'); setSubmitError(normalized.message); setFields(normalized.fields); } finally { setSaving(false); } }
  return <><section className="page-heading"><div><p className="eyebrow">Required onboarding</p><h1>Set up your Patient Profile</h1>
    <p>Your secure account is ready. Complete the required clinical profile before using patient services.</p></div><span className="step-chip">Step 2 of 2</span></section>
    <section className="profile-panel"><div className="profile-panel-head"><h2>Patient details</h2><p>Required fields are marked. Optional health information can be added now or later.</p></div>
      <PatientProfileForm initialValue={{ email: user.email, firstName: firstName(user.fullName), lastName: lastName(user.fullName) }} submitLabel="Create Patient Profile" busy={saving} serverError={submitError} serverFields={fields} onSubmit={create} /></section></>;
}
function firstName(name=''){return name.trim().split(/\s+/)[0]||'';} function lastName(name=''){return name.trim().split(/\s+/).slice(1).join(' ');}

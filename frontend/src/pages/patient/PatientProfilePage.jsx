import { useState } from 'react';
import { useLocation } from 'react-router-dom';
import { apiError } from '../../api';
import { patientsApi } from '../../api/patients';
import PatientProfileForm from '../../components/PatientProfileForm';
import ErrorAlert from '../../components/ui/ErrorAlert';
import LoadingState from '../../components/ui/LoadingState';
import { usePatientProfile } from '../../context/PatientProfileContext';

export default function PatientProfilePage() {
  const { status, profile, error, refresh, setProfile } = usePatientProfile(); const location = useLocation();
  const [editing, setEditing] = useState(false); const [saving, setSaving] = useState(false); const [submitError, setSubmitError] = useState(''); const [fields, setFields] = useState({}); const [success, setSuccess] = useState(location.state?.created ? 'Patient Profile created successfully.' : '');
  if (status === 'idle' || status === 'loading') return <LoadingState label="Loading your Patient Profile…" />;
  if (status === 'error') return <ErrorAlert message={error} onRetry={refresh} />;
  if (!profile) return null;
  async function update(payload) { setSaving(true); setSubmitError(''); setFields({}); setSuccess(''); try { const updated = await patientsApi.update(profile.id, payload); setProfile(updated); setEditing(false); setSuccess('Patient Profile updated successfully.'); }
    catch (requestError) { const normalized = apiError(requestError, 'Your Patient Profile could not be updated.'); setSubmitError(normalized.message); setFields(normalized.fields); } finally { setSaving(false); } }
  return <><section className="page-heading"><div><p className="eyebrow">Patient profile</p><h1>{profile.firstName} {profile.lastName}</h1><p>Review and maintain the clinical information connected to your account.</p></div>
    {!editing && <button onClick={() => { setEditing(true); setSuccess(''); }}>Edit profile</button>}</section>
    {success && <div className="success" role="status">{success}</div>}
    {editing ? <section className="profile-panel"><PatientProfileForm initialValue={profile} submitLabel="Save changes" busy={saving} serverError={submitError} serverFields={fields} onSubmit={update} onCancel={() => { setEditing(false); setSubmitError(''); setFields({}); }} /></section>
      : <ProfileDetails profile={profile} />}</>;
}

function ProfileDetails({ profile }) { const rows = [['Date of birth',profile.dateOfBirth],['Gender',profile.gender],['Phone',profile.phone],['Email',profile.email],['Blood group',profile.bloodGroup],['Address',profile.address],['Emergency contact',profile.emergencyContactName],['Emergency phone',profile.emergencyContactPhone],['Known allergies',profile.allergies],['Medical history summary',profile.medicalHistorySummary]];
  return <section className="profile-details"><div className="profile-summary"><span className="large-avatar">{profile.firstName[0]}{profile.lastName[0]}</span><div><strong>{profile.firstName} {profile.lastName}</strong><small>Patient Profile ID</small><code>{profile.id}</code></div></div>
    <dl>{rows.map(([label,value]) => <div key={label} className={(label==='Known allergies'||label==='Medical history summary'||label==='Address')?'detail-wide':''}><dt>{label}</dt><dd>{value || <span className="not-provided">Not provided</span>}</dd></div>)}</dl></section>; }

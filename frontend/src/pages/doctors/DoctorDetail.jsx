import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { apiError } from '../../api';
import { doctorsApi } from '../../api/doctors';
import { useAuth } from '../../context/AuthContext';
import ErrorAlert from '../../components/ui/ErrorAlert';
import LoadingState from '../../components/ui/LoadingState';

export default function DoctorDetail() {
  const { id } = useParams(); const { user } = useAuth(); const [state, setState] = useState({ loading: true, doctor: null, error: '', notFound: false, unauthorized: false });
  const load = useCallback(async () => { setState((current) => ({ ...current, loading: true, error: '' }));
    try { const doctor = await doctorsApi.directoryEntry(id); setState({ loading: false, doctor, error: '', notFound: false, unauthorized: false }); }
    catch (requestError) { const normalized = apiError(requestError, 'The doctor profile could not be loaded.'); setState({ loading: false, doctor: null, error: normalized.message, notFound: normalized.status === 404, unauthorized: normalized.status === 403 }); }
  }, [id]);
  useEffect(() => { load(); }, [load]);
  if (state.loading) return <LoadingState label="Loading professional profile…" />;
  if (state.error) return <><Link className="back-link" to="/doctors">← Back to Doctor Directory</Link><section className="directory-message"><ErrorAlert message={state.notFound ? 'This doctor profile was not found.' : state.unauthorized ? 'Your role is not authorized to view this profile.' : state.error} onRetry={state.notFound || state.unauthorized ? undefined : load} /></section></>;
  const doctor = state.doctor;
  return <><Link className="back-link" to="/doctors">← Back to Doctor Directory</Link><section className="doctor-detail-hero"><span className="doctor-avatar doctor-avatar-large">{doctor.firstName[0]}{doctor.lastName[0]}</span>
    <div><p className="eyebrow">{doctor.specialization}</p><h1>Dr. {doctor.firstName} {doctor.lastName}</h1><p>{doctor.qualification}</p></div>
    <span className={doctor.available ? 'availability available' : 'availability unavailable'}>{doctor.available ? 'Available' : 'Unavailable'}</span></section>
    <section className="doctor-detail-grid"><article><h2>Professional details</h2><dl><Row label="Specialization" value={doctor.specialization}/><Row label="Department" value={doctor.department}/>
      <Row label="Qualification" value={doctor.qualification}/><Row label="License number" value={doctor.licenseNumber}/><Row label="Experience" value={`${doctor.experienceYears} year${doctor.experienceYears === 1 ? '' : 's'}`}/></dl></article>
      <article><h2>Consultation</h2><dl><Row label="Consultation fee" value={formatFee(doctor.consultationFee)}/><Row label="Current availability" value={doctor.available ? 'Available' : 'Unavailable'}/></dl>
        {user.role==='PATIENT'&&(doctor.available?<Link className="button-link detail-book-button" to={`/appointments/new?doctorId=${doctor.id}`}>Book appointment</Link>:<p className="muted">Booking is unavailable while this doctor is marked unavailable.</p>)}</article></section></>;
}
function Row({label,value}){return <div><dt>{label}</dt><dd>{value || 'Not specified'}</dd></div>;} function formatFee(value){return value == null ? 'Not specified' : Number(value).toFixed(2);}

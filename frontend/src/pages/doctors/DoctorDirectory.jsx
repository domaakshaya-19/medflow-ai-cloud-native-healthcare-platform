import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { apiError } from '../../api';
import { doctorsApi } from '../../api/doctors';
import EmptyState from '../../components/ui/EmptyState';
import ErrorAlert from '../../components/ui/ErrorAlert';
import LoadingState from '../../components/ui/LoadingState';

export default function DoctorDirectory() {
  const [params, setParams] = useSearchParams(); const activeFilter = params.get('specialization') || '';
  const [filter, setFilter] = useState(activeFilter); const [state, setState] = useState({ loading: true, doctors: [], error: '', unauthorized: false });
  const load = useCallback(async () => { setState((current) => ({ ...current, loading: true, error: '', unauthorized: false }));
    try { const doctors = await doctorsApi.directory(activeFilter); setState({ loading: false, doctors, error: '', unauthorized: false }); }
    catch (requestError) { const normalized = apiError(requestError, 'Doctor Directory is currently unavailable.'); setState({ loading: false, doctors: [], error: normalized.message, unauthorized: normalized.status === 403 }); }
  }, [activeFilter]);
  useEffect(() => { load(); }, [load]);
  function apply(event) { event.preventDefault(); const value = filter.trim(); setParams(value ? { specialization: value } : {}); }
  return <><section className="page-heading"><div><p className="eyebrow">Care team directory</p><h1>Find a doctor</h1><p>Browse authenticated professional profiles supplied by Doctor Service.</p></div></section>
    <form className="directory-filter" onSubmit={apply}><label htmlFor="specialization">Specialization</label><div><input id="specialization" value={filter} onChange={(event) => setFilter(event.target.value)} placeholder="For example, Cardiology" maxLength="120" />
      <button>Apply filter</button>{activeFilter && <button type="button" className="secondary" onClick={() => { setFilter(''); setParams({}); }}>Clear</button>}</div></form>
    {state.loading ? <LoadingState label="Loading Doctor Directory…" /> : state.error ? <section className="directory-message"><ErrorAlert message={state.unauthorized ? 'Your role is not authorized to access the Doctor Directory.' : state.error} onRetry={state.unauthorized ? undefined : load} /></section>
      : state.doctors.length === 0 ? <EmptyState title="No doctors found" message={activeFilter ? `No doctor matched “${activeFilter}”. Try another specialization.` : 'No doctor profiles are currently available.'} />
        : <><p className="result-count">{state.doctors.length} doctor{state.doctors.length === 1 ? '' : 's'} found{activeFilter ? ` for “${activeFilter}”` : ''}</p><div className="doctor-grid">{state.doctors.map((doctor) => <DoctorCard key={doctor.id} doctor={doctor} />)}</div></>}
  </>;
}

function DoctorCard({ doctor }) { return <article className="doctor-card"><div className="doctor-card-head"><span className="doctor-avatar">{doctor.firstName[0]}{doctor.lastName[0]}</span>
  <span className={doctor.available ? 'availability available' : 'availability unavailable'}>{doctor.available ? 'Available' : 'Unavailable'}</span></div>
  <div><p className="doctor-specialization">{doctor.specialization}</p><h2>Dr. {doctor.firstName} {doctor.lastName}</h2><p className="doctor-qualification">{doctor.qualification}</p></div>
  <dl><div><dt>Department</dt><dd>{doctor.department || 'Not specified'}</dd></div><div><dt>Experience</dt><dd>{doctor.experienceYears} year{doctor.experienceYears === 1 ? '' : 's'}</dd></div></dl>
  <Link className="card-link" to={`/doctors/${doctor.id}`}>View professional profile <span aria-hidden="true">→</span></Link></article>; }

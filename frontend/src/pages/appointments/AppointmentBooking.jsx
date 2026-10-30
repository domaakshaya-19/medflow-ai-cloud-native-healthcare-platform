import { useEffect, useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { apiError } from '../../api';
import { appointmentsApi } from '../../api/appointments';
import { doctorsApi } from '../../api/doctors';
import { business } from '../../api/business';
import ErrorAlert from '../../components/ui/ErrorAlert';
import LoadingState from '../../components/ui/LoadingState';
import { useAuth } from '../../context/AuthContext';

export default function AppointmentBooking() {
  const { user } = useAuth(); const [params] = useSearchParams(); const doctorId = params.get('doctorId'); const navigate = useNavigate();
  const [doctorState, setDoctorState] = useState({ loading: true, doctor: null, error: '' });
  const [form, setForm] = useState({ appointmentDate: '', appointmentTime: '', reason: '', notes: '' }); const [fields, setFields] = useState({}); const [error, setError] = useState(''); const [saving, setSaving] = useState(false);
  const [availability,setAvailability]=useState({loading:false,times:[],error:'',limitation:''});
  useEffect(() => { if (!doctorId) { setDoctorState({ loading: false, doctor: null, error: '' }); return; }
    doctorsApi.directoryEntry(doctorId).then((doctor) => setDoctorState({ loading: false, doctor, error: '' }))
      .catch((requestError) => setDoctorState({ loading: false, doctor: null, error: apiError(requestError, 'The selected doctor could not be loaded.').message })); }, [doctorId]);
  useEffect(()=>{let active=true;if(!doctorId||!form.appointmentDate){setAvailability({loading:false,times:[],error:'',limitation:''});return;}setAvailability(s=>({...s,loading:true,error:''}));appointmentsApi.availability(doctorId,form.appointmentDate).then(data=>{if(active)setAvailability({loading:false,times:data.availableStartTimes,error:'',limitation:data.limitation});}).catch(e=>{if(active)setAvailability({loading:false,times:[],error:apiError(e,'Calendar availability could not be loaded. Booking still validates the requested start time.').message,limitation:''});});return()=>{active=false;};},[doctorId,form.appointmentDate]);
  if (user.role !== 'PATIENT') return <Navigate to="/appointments" replace />;
  if (!doctorId) return <><section className="page-heading"><div><p className="eyebrow">New appointment</p><h1>Select a doctor first</h1><p>Bookings start from a verified Doctor Directory profile.</p></div></section><Link className="button-link" to="/doctors">Open Doctor Directory</Link></>;
  if (doctorState.loading) return <LoadingState label="Loading selected doctor…" />;
  if (doctorState.error) return <><ErrorAlert message={doctorState.error}/><Link className="back-link" to="/doctors">← Choose another doctor</Link></>;
  const doctor = doctorState.doctor;
  async function submit(event){event.preventDefault();const validation=validate(form);setFields(validation);setError('');if(Object.keys(validation).length)return;setSaving(true);
    try{const created=await appointmentsApi.create({doctorId:doctor.id,...form,notes:form.notes.trim()||null,reason:form.reason.trim()});let notificationWarning='';try{await business.post('/api/notifications/events/appointment',{appointmentId:created.id});}catch{notificationWarning='Appointment booked; notification delivery could not be confirmed.';}navigate(`/appointments/${created.id}`,{replace:true,state:{created:true,notificationWarning}});}
    catch(requestError){const normalized=apiError(requestError,'The appointment could not be booked.');setError(normalized.message);setFields(normalized.fields);}finally{setSaving(false);}}
  const change=(event)=>{setForm({...form,[event.target.name]:event.target.value});setFields((current)=>({...current,[event.target.name]:undefined}));};
  return <><Link className="back-link" to={`/doctors/${doctor.id}`}>← Back to doctor profile</Link><section className="page-heading"><div><p className="eyebrow">New appointment</p><h1>Book with Dr. {doctor.firstName} {doctor.lastName}</h1><p>{doctor.specialization} · {doctor.qualification}</p></div></section>
    {!doctor.available && <ErrorAlert message="This doctor is currently marked unavailable in the directory."/>}
    <section className="booking-panel"><div className="scheduling-note"><strong>Calendar start-time availability</strong><p>{availability.limitation||'Select a date to retrieve unoccupied start times. Clinician working hours and visit durations are not modeled. The backend rechecks doctor and patient start-time conflicts transactionally.'}</p>{availability.loading&&<p role="status">Checking calendar…</p>}<ErrorAlert message={availability.error}/>{availability.times.length>0&&<label className="field"><span>Suggested unoccupied start time</span><select value={availability.times.includes(form.appointmentTime)?form.appointmentTime:''} onChange={e=>setForm(f=>({...f,appointmentTime:e.target.value}))}><option value="">Choose a calendar start time</option>{availability.times.map(time=><option key={time}>{time}</option>)}</select></label>}</div>
      <form onSubmit={submit} className="booking-form"><fieldset disabled={saving || !doctor.available}><div className="form-grid">
        <label className="field"><span>Date</span><input type="date" name="appointmentDate" min={today()} value={form.appointmentDate} onChange={change} aria-invalid={Boolean(fields.appointmentDate)}/>{fields.appointmentDate&&<small className="field-error">{fields.appointmentDate}</small>}</label>
        <label className="field"><span>Time</span><input type="time" name="appointmentTime" value={form.appointmentTime} onChange={change} aria-invalid={Boolean(fields.appointmentTime)}/>{fields.appointmentTime&&<small className="field-error">{fields.appointmentTime}</small>}</label>
        <label className="field field-wide"><span>Reason for visit</span><textarea name="reason" maxLength="500" rows="3" value={form.reason} onChange={change}/>{fields.reason&&<small className="field-error">{fields.reason}</small>}</label>
        <label className="field field-wide"><span>Notes (optional)</span><textarea name="notes" maxLength="2000" rows="4" value={form.notes} onChange={change}/></label>
      </div></fieldset><ErrorAlert message={error}/><div className="form-actions"><Link className="secondary button-link" to={`/doctors/${doctor.id}`}>Cancel</Link><button disabled={saving||!doctor.available}>{saving?'Booking…':'Book appointment'}</button></div></form></section></>;
}
function validate(form){const errors={};if(!form.appointmentDate)errors.appointmentDate='Date is required.';if(!form.appointmentTime)errors.appointmentTime='Time is required.';if(form.appointmentDate&&form.appointmentTime&&new Date(`${form.appointmentDate}T${form.appointmentTime}`)<=new Date())errors.appointmentTime='Appointment date and time must be in the future.';if(!form.reason.trim())errors.reason='Reason is required.';return errors;} function today(){return new Date().toISOString().slice(0,10);}

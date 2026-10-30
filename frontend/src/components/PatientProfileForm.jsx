import { useState } from 'react';
import FormField from './FormField';
import ErrorAlert from './ui/ErrorAlert';

const emptyProfile = { firstName: '', lastName: '', dateOfBirth: '', gender: '', phone: '', email: '', address: '', bloodGroup: '', emergencyContactName: '', emergencyContactPhone: '', allergies: '', medicalHistorySummary: '' };

export default function PatientProfileForm({ initialValue, submitLabel, busy, serverError, serverFields = {}, onSubmit, onCancel }) {
  const [form, setForm] = useState(() => ({ ...emptyProfile, ...pickFields(initialValue) }));
  const [localFields, setLocalFields] = useState({});
  const fields = { ...serverFields, ...localFields };
  const change = (event) => { setForm({ ...form, [event.target.name]: event.target.value }); setLocalFields((current) => ({ ...current, [event.target.name]: undefined })); };
  function submit(event) { event.preventDefault(); const errors = validate(form); setLocalFields(errors); if (!Object.keys(errors).length) onSubmit(normalize(form)); }
  return <form className="profile-form" onSubmit={submit} noValidate>
    <fieldset disabled={busy}><legend>Personal information</legend><div className="form-grid">
      <FormField label="First name" name="firstName" required maxLength="100" value={form.firstName} onChange={change} error={fields.firstName} />
      <FormField label="Last name" name="lastName" required maxLength="100" value={form.lastName} onChange={change} error={fields.lastName} />
      <FormField label="Date of birth" name="dateOfBirth" type="date" required max={today()} value={form.dateOfBirth} onChange={change} error={fields.dateOfBirth} />
      <FormField label="Gender (optional)" name="gender" maxLength="30" value={form.gender} onChange={change} error={fields.gender} />
      <FormField label="Phone" name="phone" type="tel" required maxLength="30" placeholder="+91 98765 43210" value={form.phone} onChange={change} error={fields.phone} />
      <FormField label="Email" name="email" type="email" required maxLength="190" value={form.email} onChange={change} error={fields.email} />
    </div></fieldset>
    <fieldset disabled={busy}><legend>Care information</legend><div className="form-grid">
      <label className="field field-wide"><span>Address (optional)</span><textarea name="address" maxLength="500" rows="3" value={form.address} onChange={change} /></label>
      <label className="field"><span>Blood group (optional)</span><select name="bloodGroup" value={form.bloodGroup} onChange={change} aria-invalid={Boolean(fields.bloodGroup)}><option value="">Not specified</option>{['A+','A-','B+','B-','AB+','AB-','O+','O-'].map((group) => <option key={group}>{group}</option>)}</select>{fields.bloodGroup && <small className="field-error">{fields.bloodGroup}</small>}</label>
      <FormField label="Emergency contact name (optional)" name="emergencyContactName" maxLength="100" value={form.emergencyContactName} onChange={change} error={fields.emergencyContactName} />
      <FormField label="Emergency contact phone (optional)" name="emergencyContactPhone" type="tel" maxLength="30" value={form.emergencyContactPhone} onChange={change} error={fields.emergencyContactPhone} />
      <label className="field field-wide"><span>Known allergies (optional)</span><textarea name="allergies" maxLength="2000" rows="3" value={form.allergies} onChange={change} /></label>
      <label className="field field-wide"><span>Medical history summary (optional)</span><textarea name="medicalHistorySummary" maxLength="4000" rows="4" value={form.medicalHistorySummary} onChange={change} /></label>
    </div></fieldset>
    <ErrorAlert message={serverError} /><div className="form-actions">{onCancel && <button type="button" className="secondary" onClick={onCancel} disabled={busy}>Cancel</button>}
      <button disabled={busy}>{busy ? 'Saving…' : submitLabel}</button></div>
  </form>;
}

function validate(form) { const errors = {}; if (!form.firstName.trim()) errors.firstName = 'First name is required.'; if (!form.lastName.trim()) errors.lastName = 'Last name is required.';
  if (!form.dateOfBirth) errors.dateOfBirth = 'Date of birth is required.'; else if (form.dateOfBirth > today()) errors.dateOfBirth = 'Date of birth cannot be in the future.';
  if (!form.phone.trim()) errors.phone = 'Phone is required.'; else if (!/^[+0-9 ()-]{7,30}$/.test(form.phone)) errors.phone = 'Use 7–30 phone characters: digits, spaces, +, parentheses, or hyphens.';
  if (!form.email.trim()) errors.email = 'Email is required.'; else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) errors.email = 'Enter a valid email address.'; return errors; }
function normalize(form) { return Object.fromEntries(Object.entries(form).map(([key, value]) => [key, typeof value === 'string' ? value.trim() : value])); }
function pickFields(profile) { if (!profile) return {}; return Object.fromEntries(Object.keys(emptyProfile).map((key) => [key, profile[key] ?? ''])); }
function today() { return new Date().toISOString().slice(0, 10); }

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { apiError } from '../api';
import { patientsApi } from '../api/patients';
import { useAuth } from './AuthContext';

const PatientProfileContext = createContext(null);
export const usePatientProfile = () => useContext(PatientProfileContext);

export function PatientProfileProvider({ children }) {
  const { user } = useAuth();
  const [state, setState] = useState({ status: 'idle', profile: null, error: '' });

  const refresh = useCallback(async () => {
    if (user?.role !== 'PATIENT') {
      setState({ status: 'not-applicable', profile: null, error: '' });
      return null;
    }
    setState((current) => ({ ...current, status: 'loading', error: '' }));
    try {
      const profile = await patientsApi.me();
      setState({ status: 'ready', profile, error: '' });
      return profile;
    } catch (requestError) {
      if (requestError.response?.status === 404) {
        setState({ status: 'missing', profile: null, error: '' });
        return null;
      }
      const error = apiError(requestError, 'Patient Profile could not be checked.').message;
      setState({ status: 'error', profile: null, error });
      return null;
    }
  }, [user?.id, user?.role]);

  useEffect(() => { refresh(); }, [refresh]);
  const setProfile = useCallback((profile) => setState({ status: 'ready', profile, error: '' }), []);
  const value = useMemo(() => ({ ...state, refresh, setProfile }), [state, refresh, setProfile]);
  return <PatientProfileContext.Provider value={value}>{children}</PatientProfileContext.Provider>;
}

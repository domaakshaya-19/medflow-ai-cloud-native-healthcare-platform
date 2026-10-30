export default function LoadingState({ label = 'Loading…', fullScreen = false }) {
  return <div className={fullScreen ? 'screen-center' : 'state-card'} role="status" aria-live="polite">
    <span className="spinner" aria-hidden="true" />
    <span>{label}</span>
  </div>;
}

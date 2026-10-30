import { Component } from 'react';
export default class ErrorBoundary extends Component {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  render() { return this.state.failed ? <main className="state-card" role="alert"><h1>This view could not be displayed</h1><p>Your data was not changed by this rendering error.</p><button onClick={() => window.location.reload()}>Reload application</button></main> : this.props.children; }
}

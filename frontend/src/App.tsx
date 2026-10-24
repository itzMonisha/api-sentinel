import React, { useState } from 'react';
import { NavigationTab } from './types';
import { Shell } from './components/layout/Shell';
import { DashboardPage } from './pages/DashboardPage';
import { ProjectsPage } from './pages/ProjectsPage';
import { EndpointsPage } from './pages/EndpointsPage';
import { TestRunsPage } from './pages/TestRunsPage';
import { SecurityScansPage } from './pages/SecurityScansPage';
import { IncidentsPage } from './pages/IncidentsPage';
import { SettingsPage } from './pages/SettingsPage';

export const App: React.FC = () => {
  const [currentTab, setCurrentTab] = useState<NavigationTab>('dashboard');
  const [activeProjectId, setActiveProjectId] = useState<string | undefined>(undefined);

  const handleNavigateToEndpoints = (projectId: string) => {
    setActiveProjectId(projectId);
    setCurrentTab('endpoints');
  };

  const renderContent = () => {
    switch (currentTab) {
      case 'dashboard':
        return <DashboardPage />;
      case 'projects':
        return <ProjectsPage onNavigateToEndpoints={handleNavigateToEndpoints} />;
      case 'endpoints':
        return <EndpointsPage initialProjectId={activeProjectId} />;
      case 'test-runs':
        return <TestRunsPage />;
      case 'security-scans':
        return <SecurityScansPage />;
      case 'incidents':
        return <IncidentsPage />;
      case 'settings':
        return <SettingsPage />;
      default:
        return <DashboardPage />;
    }
  };

  return (
    <Shell currentTab={currentTab} onSelectTab={setCurrentTab}>
      {renderContent()}
    </Shell>
  );
};

export default App;

import React from 'react';
import { Outlet } from 'react-router-dom';
import { AppHeader } from '../app-header/AppHeader';
import { AppSidebar } from '../app-sidebar/AppSidebar';

export const AppLayout: React.FC = () => {
  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <AppHeader />
      <div className="flex flex-1">
        <AppSidebar />
        <main className="flex-1 p-6 md:p-8 max-w-7xl">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

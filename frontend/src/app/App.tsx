import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { QueryProvider } from '@/app/providers/QueryProvider';
import { HomePage } from '@/pages/home/HomePage';

export const App: React.FC = () => {
  return (
    <QueryProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<HomePage />} />
        </Routes>
      </BrowserRouter>
    </QueryProvider>
  );
};

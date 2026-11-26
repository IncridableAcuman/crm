import React from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../shared/ui/card';
import { LoginForm } from '../../features/auth/ui/LoginForm';
import { Store } from 'lucide-react';

export const LoginPage: React.FC = () => {
  return (
    <div className="min-h-dvh flex items-center justify-center bg-slate-50 p-4 py-8">
      <Card className="w-full max-w-md shadow-lg border-slate-200">
        <CardHeader className="text-center space-y-2">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-xl bg-slate-900 text-white shadow-sm">
            <Store className="h-6 w-6" />
          </div>
          <div>
            <CardTitle className="text-2xl font-bold tracking-tight text-slate-900">SAVDOCRM</CardTitle>
            <CardDescription className="text-sm text-slate-500">
              Retail Store Management & POS Platform
            </CardDescription>
          </div>
        </CardHeader>
        <CardContent>
          <LoginForm />
        </CardContent>
      </Card>
    </div>
  );
};

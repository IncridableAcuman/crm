import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '../api/authApi';
import { useAuthStore } from '../model/useAuthStore';
import { Button } from '../../../shared/ui/button';
import { Input } from '../../../shared/ui/input';
import { Label } from '../../../shared/ui/label';
import { AlertCircle, Loader2 } from 'lucide-react';

const loginSchema = z.object({
  emailOrUsername: z.string().min(1, 'Email or username is required'),
  password: z.string().min(1, 'Password is required'),
});

type LoginFormData = z.infer<typeof loginSchema>;

export const LoginForm: React.FC = () => {
  const navigate = useNavigate();
  const setAuth = useAuthStore((state) => state.setAuth);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      emailOrUsername: '',
      password: '',
    },
  });

  const onSubmit = async (data: LoginFormData) => {
    setErrorMessage(null);
    try {
      const authResponse = await authApi.login(data);
      setAuth(authResponse);

      if (authResponse.activeTenant) {
        navigate('/');
      } else if (authResponse.availableTenants && authResponse.availableTenants.length > 0) {
        navigate('/select-tenant');
      } else {
        navigate('/');
      }
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.response?.data?.message || 'Invalid credentials or login failed';
      setErrorMessage(msg);
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" data-testid="login-form">
      {errorMessage && (
        <div
          role="alert"
          className="flex items-center gap-2 rounded-md bg-red-50 p-3 text-sm text-red-700 border border-red-200"
        >
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{errorMessage}</span>
        </div>
      )}

      <div className="space-y-1.5">
        <Label htmlFor="emailOrUsername">Email or Username</Label>
        <Input
          id="emailOrUsername"
          type="text"
          placeholder="admin@savdocrm.com or user123"
          autoComplete="username"
          {...register('emailOrUsername')}
          aria-invalid={!!errors.emailOrUsername}
        />
        {errors.emailOrUsername && (
          <p className="text-xs text-red-600 font-medium">{errors.emailOrUsername.message}</p>
        )}
      </div>

      <div className="space-y-1.5">
        <div className="flex items-center justify-between">
          <Label htmlFor="password">Password</Label>
        </div>
        <Input
          id="password"
          type="password"
          placeholder="••••••••"
          autoComplete="current-password"
          {...register('password')}
          aria-invalid={!!errors.password}
        />
        {errors.password && (
          <p className="text-xs text-red-600 font-medium">{errors.password.message}</p>
        )}
      </div>

      <Button type="submit" className="w-full bg-slate-900 text-white hover:bg-slate-800" disabled={isSubmitting}>
        {isSubmitting ? (
          <>
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            Authenticating...
          </>
        ) : (
          'Sign In'
        )}
      </Button>

      <div className="text-center text-sm text-slate-500 pt-2">
        Don&apos;t have an account?{' '}
        <Link to="/register" className="font-semibold text-slate-900 hover:underline">
          Register store
        </Link>
      </div>
    </form>
  );
};

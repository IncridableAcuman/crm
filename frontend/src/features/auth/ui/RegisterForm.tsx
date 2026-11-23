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

const registerSchema = z.object({
  email: z.string().email('Please enter a valid email address'),
  username: z
    .string()
    .min(3, 'Username must be at least 3 characters')
    .max(50, 'Username cannot exceed 50 characters')
    .regex(/^[a-zA-Z0-9_.-]+$/, 'Alphanumeric, dots, hyphens, and underscores only'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
  phoneNumber: z.string().optional(),
  storeName: z.string().optional(),
});

type RegisterFormData = z.infer<typeof registerSchema>;

export const RegisterForm: React.FC = () => {
  const navigate = useNavigate();
  const setAuth = useAuthStore((state) => state.setAuth);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      email: '',
      username: '',
      password: '',
      firstName: '',
      lastName: '',
      phoneNumber: '',
      storeName: '',
    },
  });

  const onSubmit = async (data: RegisterFormData) => {
    setErrorMessage(null);
    try {
      const authResponse = await authApi.register(data);
      setAuth(authResponse);

      if (authResponse.activeTenant) {
        navigate('/');
      } else if (authResponse.availableTenants && authResponse.availableTenants.length > 0) {
        navigate('/select-tenant');
      } else {
        navigate('/');
      }
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.response?.data?.message || 'Registration failed';
      setErrorMessage(msg);
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-3" data-testid="register-form">
      {errorMessage && (
        <div
          role="alert"
          className="flex items-center gap-2 rounded-md bg-red-50 p-3 text-sm text-red-700 border border-red-200"
        >
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{errorMessage}</span>
        </div>
      )}

      <div className="grid grid-cols-2 gap-2">
        <div className="space-y-1">
          <Label htmlFor="firstName">First Name</Label>
          <Input id="firstName" placeholder="John" {...register('firstName')} />
          {errors.firstName && <p className="text-xs text-red-600">{errors.firstName.message}</p>}
        </div>
        <div className="space-y-1">
          <Label htmlFor="lastName">Last Name</Label>
          <Input id="lastName" placeholder="Doe" {...register('lastName')} />
          {errors.lastName && <p className="text-xs text-red-600">{errors.lastName.message}</p>}
        </div>
      </div>

      <div className="space-y-1">
        <Label htmlFor="email">Email Address</Label>
        <Input id="email" type="email" placeholder="john.doe@example.com" {...register('email')} />
        {errors.email && <p className="text-xs text-red-600">{errors.email.message}</p>}
      </div>

      <div className="space-y-1">
        <Label htmlFor="username">Username</Label>
        <Input id="username" placeholder="johndoe" {...register('username')} />
        {errors.username && <p className="text-xs text-red-600">{errors.username.message}</p>}
      </div>

      <div className="space-y-1">
        <Label htmlFor="password">Password (min 8 characters)</Label>
        <Input id="password" type="password" placeholder="••••••••" {...register('password')} />
        {errors.password && <p className="text-xs text-red-600">{errors.password.message}</p>}
      </div>

      <div className="space-y-1">
        <Label htmlFor="phoneNumber">Phone Number (optional)</Label>
        <Input id="phoneNumber" placeholder="+1 234 567 8900" {...register('phoneNumber')} />
      </div>

      <div className="space-y-1 border-t border-slate-100 pt-2">
        <Label htmlFor="storeName">Retail Store Name (optional)</Label>
        <Input id="storeName" placeholder="e.g. Acme Supermarket" {...register('storeName')} />
        <p className="text-xs text-slate-500">Creating a store will automatically establish your initial tenant store.</p>
      </div>

      <Button type="submit" className="w-full bg-slate-900 text-white hover:bg-slate-800" disabled={isSubmitting}>
        {isSubmitting ? (
          <>
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            Creating Account...
          </>
        ) : (
          'Create Store Account'
        )}
      </Button>

      <div className="text-center text-sm text-slate-500 pt-1">
        Already have an account?{' '}
        <Link to="/login" className="font-semibold text-slate-900 hover:underline">
          Sign In
        </Link>
      </div>
    </form>
  );
};

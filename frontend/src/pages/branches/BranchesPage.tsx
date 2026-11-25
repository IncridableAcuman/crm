import React, { useState } from 'react';
import { useBranches, useCreateBranch } from '@/entities/branch';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { Badge } from '@/shared/ui/badge';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/shared/ui/card';
import {
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
} from '@/shared/ui/table';
import {
  Dialog,
  DialogTrigger,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/shared/ui/dialog';
import { PermissionGuard } from '@/entities/permission';
import { parseApiError } from '@/shared/api/errorHandler';
import { GitBranch, Plus, Phone, MapPin, Loader2, CheckCircle2, Star } from 'lucide-react';

const BRANCH_CODE_REGEX = /^[A-Z0-9_-]{2,30}$/;

export const BranchesPage: React.FC = () => {
  const { data: branches = [], isLoading, error, refetch } = useBranches();
  const createBranchMutation = useCreateBranch();

  const [open, setOpen] = useState(false);
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');
  const [isMain, setIsMain] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const resetForm = () => {
    setCode('');
    setName('');
    setPhone('');
    setAddress('');
    setIsMain(false);
    setFormError(null);
  };

  const handleOpenChange = (nextOpen: boolean) => {
    setOpen(nextOpen);
    if (!nextOpen) {
      resetForm();
    } else {
      setFormError(null);
    }
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    const normalizedCode = code.trim().toUpperCase();
    const trimmedName = name.trim();

    if (!normalizedCode || !trimmedName) {
      setFormError('Branch code and name are required.');
      return;
    }

    if (!BRANCH_CODE_REGEX.test(normalizedCode)) {
      setFormError('Branch code must be 2 to 30 characters using uppercase letters, numbers, hyphens, or underscores.');
      return;
    }

    if (trimmedName.length > 150) {
      setFormError('Branch name cannot exceed 150 characters.');
      return;
    }

    if (phone.trim().length > 30) {
      setFormError('Phone cannot exceed 30 characters.');
      return;
    }

    try {
      await createBranchMutation.mutateAsync({
        code: normalizedCode,
        name: trimmedName,
        phone: phone.trim() || null,
        address: address.trim() || null,
        isMain,
      });
      resetForm();
      setOpen(false);
    } catch (err: unknown) {
      setFormError(parseApiError(err, 'Failed to create branch. Please check input values.').message);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <GitBranch className="h-6 w-6 text-slate-900" />
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">Store Branches</h1>
          </div>
          <p className="text-sm text-slate-500 mt-1">
            Manage your retail store branches, locations, and primary branch designation.
          </p>
        </div>

        <PermissionGuard permission="tenancy.branches.manage">
          <Dialog open={open} onOpenChange={handleOpenChange}>
            <DialogTrigger asChild>
              <Button size="sm" className="gap-1.5 bg-slate-900 hover:bg-slate-800">
                <Plus className="h-4 w-4" />
                <span>New Branch</span>
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Add Store Branch</DialogTitle>
                <DialogDescription>
                  Create a physical or virtual retail branch under the current active store.
                </DialogDescription>
              </DialogHeader>

              <form onSubmit={handleCreate} className="space-y-4 py-2">
                {formError && (
                  <div role="alert" className="rounded-md bg-red-50 p-3 text-xs text-red-700 border border-red-200">
                    {formError}
                  </div>
                )}

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <Label htmlFor="branch-code">Branch Code *</Label>
                    <Input
                      id="branch-code"
                      placeholder="e.g. BR-01"
                      value={code}
                      onChange={(e) => setCode(e.target.value.toUpperCase())}
                      required
                    />
                    <span className="text-[11px] text-slate-400">2–30 chars (A–Z, 0–9, _, -)</span>
                  </div>

                  <div className="space-y-1.5">
                    <Label htmlFor="branch-name">Branch Name *</Label>
                    <Input
                      id="branch-name"
                      placeholder="e.g. Chorsu Branch"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      required
                    />
                  </div>
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="branch-phone">Phone Number</Label>
                  <Input
                    id="branch-phone"
                    placeholder="+998901234567"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                  />
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="branch-address">Physical Address</Label>
                  <Input
                    id="branch-address"
                    placeholder="e.g. Navoi St. 24, Tashkent"
                    value={address}
                    onChange={(e) => setAddress(e.target.value)}
                  />
                </div>

                <label className="flex items-center gap-2 rounded-md border border-slate-200 p-3 text-xs text-slate-700 cursor-pointer hover:bg-slate-50">
                  <input
                    id="branch-is-main"
                    type="checkbox"
                    checked={isMain}
                    onChange={(e) => setIsMain(e.target.checked)}
                    className="rounded border-slate-300"
                  />
                  <div>
                    <div className="font-medium text-slate-900">Designate as Main Branch</div>
                    <div className="text-[11px] text-slate-500">
                      Marks this location as a flagship / main store branch.
                    </div>
                  </div>
                </label>

                <DialogFooter className="pt-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => handleOpenChange(false)}
                    disabled={createBranchMutation.isPending}
                  >
                    Cancel
                  </Button>
                  <Button
                    type="submit"
                    className="bg-slate-900 hover:bg-slate-800"
                    disabled={createBranchMutation.isPending}
                  >
                    {createBranchMutation.isPending && (
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    )}
                    Create Branch
                  </Button>
                </DialogFooter>
              </form>
            </DialogContent>
          </Dialog>
        </PermissionGuard>
      </div>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Active Branches</CardTitle>
          <CardDescription className="text-xs">
            Physical stores and points of distribution configured for this tenant.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="flex h-32 items-center justify-center text-slate-400">
              <Loader2 className="h-6 w-6 animate-spin mr-2" />
              <span>Loading store branches...</span>
            </div>
          ) : error ? (
            <div role="alert" className="p-4 text-center text-sm text-red-600 bg-red-50 rounded-lg space-y-2">
              <div>{parseApiError(error, 'Failed to load branches.').message}</div>
              <Button variant="outline" size="sm" onClick={() => void refetch()}>
                Retry
              </Button>
            </div>
          ) : branches.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-8 text-center text-slate-500">
              <GitBranch className="h-10 w-10 text-slate-300 mb-2" />
              <p className="font-medium text-slate-700">No branches created yet</p>
              <p className="text-xs text-slate-400 mt-1 max-w-sm">
                Branches allow you to scope cash registers, warehouse stock, and cashier permissions to specific locations.
              </p>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Code</TableHead>
                  <TableHead>Branch Name</TableHead>
                  <TableHead>Contact & Address</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {branches.map((branch) => (
                  <TableRow key={branch.id}>
                    <TableCell className="font-mono text-xs font-semibold text-slate-900">
                      {branch.code}
                    </TableCell>
                    <TableCell className="font-medium text-slate-900">
                      {branch.name}
                    </TableCell>
                    <TableCell className="text-xs text-slate-600">
                      {branch.phone && (
                        <div className="flex items-center gap-1.5">
                          <Phone className="h-3 w-3 text-slate-400" />
                          <span>{branch.phone}</span>
                        </div>
                      )}
                      {branch.address && (
                        <div className="flex items-center gap-1.5 text-slate-500 mt-0.5">
                          <MapPin className="h-3 w-3 text-slate-400" />
                          <span>{branch.address}</span>
                        </div>
                      )}
                      {!branch.phone && !branch.address && (
                        <span className="text-slate-400 italic">No contact details</span>
                      )}
                    </TableCell>
                    <TableCell>
                      {branch.isMain ? (
                        <Badge variant="info" className="gap-1 font-normal text-[11px]">
                          <Star className="h-3 w-3" />
                          Main
                        </Badge>
                      ) : (
                        <Badge variant="outline" className="font-normal text-[11px] text-slate-600">
                          Branch
                        </Badge>
                      )}
                    </TableCell>
                    <TableCell>
                      {branch.isActive ? (
                        <Badge variant="success" className="gap-1 font-normal text-[11px]">
                          <CheckCircle2 className="h-3 w-3" />
                          Active
                        </Badge>
                      ) : (
                        <Badge variant="destructive" className="font-normal text-[11px]">
                          Inactive
                        </Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-xs text-slate-500 font-mono">
                      {new Date(branch.createdAt).toLocaleDateString()}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

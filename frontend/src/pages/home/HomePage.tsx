import React from 'react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/shared/ui/card';
import { Button } from '@/shared/ui/button';
import { CheckCircle2, Server, Layers, ShieldCheck, Database, LayoutDashboard } from 'lucide-react';

export const HomePage: React.FC = () => {
  return (
    <div className="container mx-auto py-10 px-4 max-w-5xl">
      <div className="flex items-center justify-between pb-8 border-b">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-primary">SAVDOCRM</h1>
          <p className="text-muted-foreground mt-1">
            Store Management & Retail Point of Sale Platform
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className="inline-flex items-center rounded-full bg-green-50 px-2.5 py-0.5 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-600/20">
            <CheckCircle2 className="w-3.5 h-3.5 mr-1" />
            Milestone 01 Ready
          </span>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mt-8">
        <Card>
          <CardHeader>
            <Server className="w-6 h-6 text-primary mb-2" />
            <CardTitle>Backend Foundation</CardTitle>
            <CardDescription>Modular Monolith on Java 21</CardDescription>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground space-y-2">
            <div>• Spring Boot 4.2.0 (Spring 7.1)</div>
            <div>• Gradle Wrapper 9.8.2</div>
            <div>• Virtual Threads Enabled</div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <Database className="w-6 h-6 text-primary mb-2" />
            <CardTitle>Data Tier</CardTitle>
            <CardDescription>PostgreSQL 16+ & Liquibase</CardDescription>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground space-y-2">
            <div>• Row-Level Tenant Isolation</div>
            <div>• Composite Foreign Keys</div>
            <div>• Decimal Precision Accounting</div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <Layers className="w-6 h-6 text-primary mb-2" />
            <CardTitle>Frontend Architecture</CardTitle>
            <CardDescription>Feature-Sliced Design</CardDescription>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground space-y-2">
            <div>• React 19 + TypeScript (Strict)</div>
            <div>• Tailwind CSS & shadcn/ui</div>
            <div>• TanStack Query & Zustand</div>
          </CardContent>
        </Card>
      </div>

      <Card className="mt-8">
        <CardHeader>
          <div className="flex items-center gap-2">
            <ShieldCheck className="w-5 h-5 text-primary" />
            <CardTitle className="text-lg">Project Foundation Status</CardTitle>
          </div>
          <CardDescription>
            Milestone 01 setup is complete and validated. Ready for Milestone 02 (Multi-tenancy).
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="rounded-lg bg-muted p-4 font-mono text-xs space-y-1">
            <div className="text-green-600 font-semibold">✓ Backend build: BUILD SUCCESSFUL (./gradlew build)</div>
            <div className="text-green-600 font-semibold">✓ Baseline test: SavdoCrmApplicationTests PASSED</div>
            <div className="text-green-600 font-semibold">✓ Liquibase changelog: Initialized</div>
            <div className="text-green-600 font-semibold">✓ Docker Compose: PostgreSQL 16 container defined</div>
            <div className="text-green-600 font-semibold">✓ Frontend FSD structure: Initialized</div>
          </div>
          <div className="mt-6 flex justify-end">
            <Button variant="default">
              <LayoutDashboard className="w-4 h-4 mr-2" />
              Explore System Health (/api/v1/health)
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export interface UnitOfMeasure {
  id: string;
  code: string;
  name: string;
  symbol: string;
  allowDecimal: boolean;
  decimalPrecision: number;
  isSystem: boolean;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface UnitSummary {
  id: string;
  code: string;
  name: string;
  symbol: string;
  allowDecimal: boolean;
  decimalPrecision: number;
}

export interface CreateUnitRequest {
  code: string;
  name: string;
  symbol: string;
  allowDecimal: boolean;
  decimalPrecision: number;
}

export interface UpdateUnitRequest {
  name: string;
  symbol: string;
  allowDecimal: boolean;
  decimalPrecision: number;
}

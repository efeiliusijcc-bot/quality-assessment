export interface ApiResponse<T> {
  code: number;
  data: T;
  message?: string;
  msg?: string;
}

export interface ApiError {
  code: number;
  message: string;
}

export interface PaginationQuery {
  page: number;
  pageSize: number;
}

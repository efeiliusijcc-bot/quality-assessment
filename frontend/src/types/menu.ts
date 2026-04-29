export interface MenuItem {
  path: string;
  title: string;
  icon?: string;
  children?: MenuItem[];
}

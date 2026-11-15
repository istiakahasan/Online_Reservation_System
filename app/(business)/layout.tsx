import type { ReactNode } from "react";

export default function BusinessLayout({ children }: { children: ReactNode }) {
  return (
    <div className="business-layout-container">
      {children}
    </div>
  );
}

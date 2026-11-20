import type {
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from "react";

type InputFieldProps = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "onChange" | "value"
> & {
  label: string;
  value: string | number | null;
  onValueChange: (value: string) => void;
};

export function InputField({
  label,
  value,
  onValueChange,
  ...props
}: InputFieldProps) {
  return (
    <label className="field">
      <span>{label}</span>
      <input
        {...props}
        value={value ?? ""}
        onChange={(event) => onValueChange(event.target.value)}
      />
    </label>
  );
}

type TextAreaFieldProps = Omit<
  TextareaHTMLAttributes<HTMLTextAreaElement>,
  "onChange" | "value"
> & {
  label: string;
  value: string;
  onValueChange: (value: string) => void;
};

export function TextAreaField({
  label,
  value,
  onValueChange,
  ...props
}: TextAreaFieldProps) {
  return (
    <label className="field">
      <span>{label}</span>
      <textarea
        {...props}
        value={value}
        onChange={(event) => onValueChange(event.target.value)}
      />
    </label>
  );
}

type SelectFieldProps = Omit<
  SelectHTMLAttributes<HTMLSelectElement>,
  "onChange" | "value"
> & {
  label: string;
  value: string;
  options: { value: string; label: string }[];
  onValueChange: (value: string) => void;
};

export function SelectField({
  label,
  value,
  options,
  onValueChange,
  ...props
}: SelectFieldProps) {
  return (
    <label className="field">
      <span>{label}</span>
      <select
        {...props}
        value={value}
        onChange={(event) => onValueChange(event.target.value)}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}

export function CheckLine({
  checked,
  label,
  onChange,
}: {
  checked: boolean;
  label: string;
  onChange: (checked: boolean) => void;
}) {
  return (
    <label className="check-line">
      <input
        checked={checked}
        type="checkbox"
        onChange={(event) => onChange(event.target.checked)}
      />
      <span>{label}</span>
    </label>
  );
}

export function PageHeading({
  eyebrow,
  title,
  description,
  children,
}: {
  eyebrow: string;
  title: string;
  description: string;
  children?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <span className="eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {children}
    </div>
  );
}

export function Alert({
  tone,
  children,
}: {
  tone: "error" | "success";
  children: ReactNode;
}) {
  return (
    <div className={`alert alert-${tone}`} role={tone === "error" ? "alert" : "status"}>
      {children}
    </div>
  );
}

export function SectionTitle({
  number,
  title,
  description,
}: {
  number: string;
  title: string;
  description: string;
}) {
  return (
    <div className="section-title">
      <span className="section-number">{number}</span>
      <div>
        <h3>{title}</h3>
        <p>{description}</p>
      </div>
    </div>
  );
}

export function EmptyState({
  icon,
  title,
  description,
  compact = false,
}: {
  icon: string;
  title: string;
  description: string;
  compact?: boolean;
}) {
  return (
    <div className={`empty-state${compact ? " compact-empty" : ""}`}>
      <span className="empty-illustration">{icon}</span>
      <strong>{title}</strong>
      <p>{description}</p>
    </div>
  );
}

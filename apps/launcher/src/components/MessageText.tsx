import { presentMessage } from "../services/messages";

export function MessageText({
  value,
  language,
  diagnostics = false,
}: {
  value: unknown;
  language: "de" | "en";
  diagnostics?: boolean;
}) {
  const message = presentMessage(value, language);
  return (
    <div className="message-content">
      <span>{message.text}</span>
      {diagnostics && message.details && (
        <details className="message-diagnostics">
          <summary>
            {language === "de" ? "Technische Details" : "Technical details"}
          </summary>
          <pre>{message.details}</pre>
        </details>
      )}
    </div>
  );
}

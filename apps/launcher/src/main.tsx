import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import "./styles/app.css";
import "./styles/polish.css";
import "./styles/refinement.css";
import "./styles/window-layout.css";
import "./styles/studio.css";
import "./styles/interaction.css";
import "./styles/installations.css";
createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
import "./styles/play.css";
import "./styles/hover.css";

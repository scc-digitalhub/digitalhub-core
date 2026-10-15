import { ConsoleExtensionModule } from "@digitalhub/console/features/extensions/ConsoleExtension";
import { ComplianceIcon } from "./components/icon";
import { AiCompliancePage } from "./components/AiCompliancePage";
import { AiComplianceModelPage } from "./components/AiComplianceModelPage";
import { AiComplianceDataPage } from "./components/AiComplianceDataPage";

import { en } from "./components/compliance-widgets/i18n/resources/en";
import { it } from "./components/compliance-widgets/i18n/resources/it";
import artifacts from "@digitalhub/console/pages/artifacts";
  

const hubModule: ConsoleExtensionModule = {
  components: {
    projectCompliancePage: AiCompliancePage,
    dataCompliancePage: AiComplianceDataPage,
    modelCompliancePage: AiComplianceModelPage,
    menuIcon: ComplianceIcon,
  },
  views: {
    projects: {
      show: [
        {
          showIn: "menu",
          component: "projectCompliancePage",
          label: "Compliance",
          icon: "menuIcon",
        },
      ],
    },
    artifacts: {
      show: [
        {
          showIn: "tab",
          component: "dataCompliancePage",
          label: "Compliance",
        },
      ],
    },
    dataitems: {
      show: [
        {
          showIn: "tab",
          component: "dataCompliancePage",
          label: "Compliance",
        },
      ],
    },
    models: {
      show: [
        {
          showIn: "tab",
          component: "modelCompliancePage",
          label: "Compliance",
        },
      ],
    }
  },
  i18n: {
    en: en,
    it: it,
  },
};

export default hubModule;
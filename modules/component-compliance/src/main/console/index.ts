import { ConsoleExtensionModule } from "@digitalhub/console/features/extensions/ConsoleExtension";
import { ComplianceIcon } from "./components/icon";
import { AiCompliancePage } from "./components/AiCompliancePage";

import { en } from "./components/compliance-widgets/i18n/resources/en";
import { it } from "./components/compliance-widgets/i18n/resources/it";
  

const hubModule: ConsoleExtensionModule = {
  components: {
    projectCompliancePage: AiCompliancePage,
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
  },
  i18n: {
    en: en,
    it: it,
  },
};

export default hubModule;
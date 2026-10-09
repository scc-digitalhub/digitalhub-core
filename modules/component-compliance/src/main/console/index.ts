import { ConsoleExtensionModule } from "@digitalhub/console/features/extensions/ConsoleExtension";
import { ComplianceIcon } from "./components/icon";
import { AiCompliancePage } from "./components/AiCompliancePage";

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
};

export default hubModule;
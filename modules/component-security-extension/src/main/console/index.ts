import { ConsoleExtensionModule } from "@digitalhub/console/features/extensions/ConsoleExtension";
import { SecurityIcon } from "./components/icon";
import { SecurityReportsPage } from "./components/SecurityReportsPage";

import { en } from "./components/i18n/resources/en";
import { it } from "./components/i18n/resources/it";
  

const hubModule: ConsoleExtensionModule = {
  components: {
    securityReportsPage: SecurityReportsPage,
    menuIcon: SecurityIcon,
  },
  views: {
    containerimages: {
      show: [
        {
          showIn: "tab",
          component: "securityReportsPage",
          label: "Security",
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
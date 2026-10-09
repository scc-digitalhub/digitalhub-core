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
    // functions: {
    //   list: [
    //     {
    //       showIn: "toolbar",
    //       component: "hubButton",
    //       icon: "menuIcon",
    //     },
    //   ],
    //   create: [
    //     {
    //       showIn: "toolbar",
    //       component: "hubButton",
    //     },
    //     {
    //       showIn: "tab",
    //       component: "hubButton",
    //       label: "Hub Test Step",
    //     },
    //   ],
    //   edit: [
    //     {
    //       showIn: "toolbar",
    //       component: "hubButton",
    //     },
    //     {
    //       showIn: "section",
    //       component: "hubButton",
    //       label: "Hub Test Section",
    //     },
    //   ],
    //   show: [
    //     {
    //       showIn: "toolbar",
    //       component: "hubButton",
    //     },
    //     {
    //       showIn: "tab",
    //       component: "hubButton",
    //       label: "Hub Test Section",

    //     },
    //   ],
    // },
  },
};

export default hubModule;
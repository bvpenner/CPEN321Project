import { JestConfigWithTsJest } from "ts-jest";

const config: JestConfigWithTsJest = {
  preset: "ts-jest",
  testEnvironment: "node",
  transform: {
    "^.+\\.ts$": ["ts-jest", { useESM: true }],
  },
  extensionsToTreatAsEsm: [".ts"],
  moduleNameMapper: {
    "^(\\.{1,2}/.*)\\.js$": "$1",
  },
  forceExit: true,
  detectOpenHandles: false,

  globals: {
    "ts-jest": {
      diagnostics: {
        ignoreCodes: [6133],
        warnOnly: true,
      },
    },
  },
};

export default config;

import { fileURLToPath } from 'node:url';
import { expect } from '@esm-bundle/chai';
import { rimraf } from 'rimraf';
import type { RouteMeta } from '../../src/vite-plugin/collectRoutesFromFS.js';
import createViewConfigJson from '../../src/vite-plugin/createViewConfigJson.js';
import { RouteParamType } from '../../src/vite-plugin/utils.js';
import { createTestingRouteFiles, createTestingRouteMeta, createTmpDir } from '../utils.js';

describe('@vaadin/hilla-file-router', () => {
  describe('generateJson', () => {
    let tmp: URL;
    let meta: RouteMeta;

    before(async () => {
      tmp = await createTmpDir();
      await createTestingRouteFiles(tmp);
    });

    after(async () => {
      await rimraf(fileURLToPath(tmp));
    });

    beforeEach(() => {
      meta = createTestingRouteMeta(tmp);
    });

    it('should generate a JSON representation of the route tree', async () => {
      const generated = await createViewConfigJson(meta);

      expect(generated).to.equal(
        JSON.stringify({
          '/about': { route: 'about', title: 'About' },
          '/profile/': { title: 'Profile' },
          '/profile/account/security/password': { title: 'Password' },
          '/profile/account/security/two-factor-auth': { title: 'Two Factor Auth' },
          '/profile/friends/list': { title: 'List' },
          '/profile/friends/:user': { title: 'User', params: { ':user': RouteParamType.Required } },
          '/test/*': { params: { '*': RouteParamType.Wildcard } },
          '/test/:optional?': { params: { ':optional?': RouteParamType.Optional } },
        }),
      );
    });
  });
});

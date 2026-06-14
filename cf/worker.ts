import { Container } from "cloudflare:containers";

/**
 * Spring Boot listens on 8080. sleepAfter is set beyond the 10-min OAuth2
 * token TTL so a token issued just before idle won't be rejected mid-flight.
 */
export class InventoryContainer extends Container {
  defaultPort = 8080;
  sleepAfter = "2 hours";
}

interface Env {
  INVENTORY_CONTAINER: DurableObjectNamespace<InventoryContainer>;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const id = env.INVENTORY_CONTAINER.idFromName("singleton");
    const stub = env.INVENTORY_CONTAINER.get(id);
    return stub.fetch(request);
  },
} satisfies ExportedHandler<Env>;

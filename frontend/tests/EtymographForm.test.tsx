// @vitest-environment jsdom
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import {afterEach, expect, it, vi} from "vitest";
import {MantineProvider} from "@mantine/core";
import EtymographForm from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import FormTextArea from "@/components/FormTextArea";

vi.mock("next/router", () => ({useRouter: () => ({})}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
window.matchMedia = vi.fn().mockImplementation((media: string) => ({
    media, matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn(),
}));

let root: Root;
let container: HTMLDivElement;

afterEach(async () => {
    if (root) await act(() => root.unmount());
    container?.remove();
});

async function renderForm(create = vi.fn(async () => new Response(null, {status: 200})), onKeyDown = vi.fn()) {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    await act(() => root.render(
        <MantineProvider env="test"><EtymographForm defaultValues={{text: "Word", notes: "Notes"}} create={create}>
            <table><tbody><FormRow id="text" label="Word"/></tbody></table>
            <div onKeyDown={onKeyDown}><FormTextArea id="notes"/></div>
        </EtymographForm></MantineProvider>
    ));
    return create;
}

it.each([
    ["text", "metaKey"],
    ["text", "ctrlKey"],
    ["notes", "metaKey"],
    ["notes", "ctrlKey"],
])("submits once with current values from %s using %s-Enter", async (id, modifier) => {
    const create = await renderForm();
    const field = container.querySelector<HTMLInputElement | HTMLTextAreaElement>(`#${id}`)!;
    await act(() => {
        // Use the native setter so React observes the edited value.
        const prototype = id === "notes" ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
        Object.getOwnPropertyDescriptor(prototype, "value")!.set!.call(field, "Edited");
        field.dispatchEvent(new Event("input", {bubbles: true}));
    });
    const event = new KeyboardEvent("keydown", {key: "Enter", [modifier]: true, bubbles: true, cancelable: true});
    await act(() => { field.dispatchEvent(event); });

    expect(event.defaultPrevented).toBe(true);
    expect(create).toHaveBeenCalledExactlyOnceWith({
        text: id === "text" ? "Edited" : "Word",
        notes: id === "notes" ? "Edited" : "Notes",
    });
});

it("leaves plain Enter in a textarea available for a newline", async () => {
    const create = await renderForm();
    const event = new KeyboardEvent("keydown", {key: "Enter", bubbles: true, cancelable: true});
    await act(() => { container.querySelector("textarea")!.dispatchEvent(event); });

    expect(event.defaultPrevented).toBe(false);
    expect(create).not.toHaveBeenCalled();
});

it("handles the shortcut before input widgets or enclosing dialogs can act on Enter", async () => {
    const onKeyDown = vi.fn();
    const create = await renderForm(undefined, onKeyDown);
    const dialogKeyDown = vi.fn();
    container.addEventListener("keydown", dialogKeyDown);
    await act(() => { container.querySelector("textarea")!.dispatchEvent(new KeyboardEvent("keydown", {
        key: "Enter", metaKey: true, bubbles: true, cancelable: true,
    })); });

    expect(create).toHaveBeenCalledTimes(1);
    expect(onKeyDown).not.toHaveBeenCalled();
    expect(dialogKeyDown).not.toHaveBeenCalled();
});

it("does not submit again while a save is pending", async () => {
    let finishSave: (response: Response) => void;
    const create = await renderForm(vi.fn(() => new Promise<Response>(resolve => { finishSave = resolve; })));
    const field = container.querySelector("textarea")!;
    const pressShortcut = () => field.dispatchEvent(new KeyboardEvent("keydown", {
        key: "Enter", metaKey: true, bubbles: true, cancelable: true,
    }));

    await act(() => { pressShortcut(); });
    expect(container.querySelector<HTMLInputElement>('input[type="submit"]')!.disabled).toBe(true);
    await act(() => { pressShortcut(); });
    await act(() => { finishSave(new Response(null, {status: 200})); });

    expect(create).toHaveBeenCalledTimes(1);
});

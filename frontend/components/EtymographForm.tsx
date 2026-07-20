import {createContext, startTransition, useActionState, useContext, useEffect, useState} from "react";
import {useRouter} from "next/router";
import {EditModeContext, SetEditModeContext} from "@/components/EditModeContexts";
import {useForm} from "@mantine/form";

export interface EtymographFormButton {
    text: string;
    callback: (data: any) => void;
}

export interface EtymographFormProps<Data, ResponseData=Data> {
    defaultValues?: Data
    setEditMode?: (newEditMode: boolean) => void
    focusTarget?: string
    updateId?: string | number
    update?: (Data: Data) => Promise<Response>
    create?: (Data: Data) => Promise<Response>
    children?: React.ReactNode
    submitted?: (responseData: ResponseData, data: Data) => any
    redirectOnCreate?: (Data: ResponseData) => string
    buttons?: EtymographFormButton[]
    cancelled?: () => void
    saveButtonText?: string
}

const EtymographFormContext = createContext<any>(null)

export function useEtymographFormContext() {
    const form = useContext(EtymographFormContext)
    if (form === null) {
        throw new Error("useEtymographFormContext must be used within EtymographForm")
    }
    return form
}

export default function EtymographForm<Data, ResponseData=Data>(props: EtymographFormProps<Data, ResponseData>) {
    const form = useForm({
        mode: "uncontrolled",
        initialValues: (props.defaultValues ?? {}) as any
    });
    const router = useRouter()
    const editMode = useContext(EditModeContext)
    const setEditModeContext = useContext(SetEditModeContext)
    const setEditMode = props.setEditMode ?? setEditModeContext
    const [focusTarget, setFocusTarget] = useState(props.focusTarget)

    useEffect(() => {
        if (focusTarget) {
            const element = document.getElementById(focusTarget) as HTMLInputElement | HTMLTextAreaElement | null
            element?.focus()
            setFocusTarget(null)
        }
    }, [focusTarget])

    const [errorText, submitAction, isPending] = useActionState(
        async (_previousError: string, data: Data) => {
            const request = props.updateId !== undefined ? props.update : props.create
            if (request === undefined) return "Form submission is not configured"

            let responseData: ResponseData | undefined
            try {
                const response = await request(data)
                if (response.status !== 200) {
                    const error = await response.json()
                    return error.message?.length > 0 ? error.message : "Failed to save form"
                }

                const hasJsonResponse = response.headers.get("content-type")?.includes("application/json") === true
                responseData = hasJsonResponse ? await response.json() as ResponseData : undefined
            }
            catch (error) {
                return error instanceof Error ? error.message : "Failed to save form"
            }

            if (responseData !== undefined && props.redirectOnCreate !== undefined) {
                void router.push(props.redirectOnCreate(responseData))
            }
            else if (props.submitted !== undefined) {
                const result = await props.submitted(responseData, data)
                if (result?.message !== undefined) return result.message
            }
            else if (setEditMode !== undefined) {
                await router.replace(router.asPath)
                setEditMode(false)
            }
            return ""
        },
        ""
    )

    function saveForm(data: Data) {
        startTransition(() => submitAction(data))
    }

    if (editMode === false) return <></>
    const buttons = props.buttons || []

    return <EtymographFormContext.Provider value={form}>
        <form onSubmit={form.onSubmit(saveForm)}>
            {props.children}
            <p>
                <input type="submit" value={isPending ? "Saving…" : props.saveButtonText ?? "Save"}
                       className="uiButtonSubmit" disabled={isPending}/>
                {(props.cancelled !== undefined || setEditMode !== undefined) && <>{' '}
                    <button type="button" className="uiButton" disabled={isPending}
                            onClick={() => props.cancelled !== undefined ? props.cancelled() : setEditMode(false)}>Cancel</button>
                </>}
                {buttons.map(b => <>{' '}<button type="button" className="uiButton" disabled={isPending}
                    onClick={() => b.callback(form.getValues())}>{b.text}</button></>)}
            </p>
            {errorText !== "" && <div className="errorText">{errorText}</div>}
        </form>
    </EtymographFormContext.Provider>
}

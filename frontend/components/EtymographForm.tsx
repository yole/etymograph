import {createContext, useContext, useEffect, useState} from "react";
import {useRouter} from "next/router";
import {EditModeContext, SetEditModeContext} from "@/components/EtymographFormView";
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
    const [, setFormVersion] = useState(0)
    const form = useForm({
        mode: "uncontrolled",
        initialValues: (props.defaultValues ?? {}) as any,
        onValuesChange: () => setFormVersion((v) => v + 1),
    });
    const [errorText, setErrorText] = useState("")
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

    async function saveForm(data: Data) {
        const r = props.updateId !== undefined ? await props.update(data) : await props.create(data)
        if (r.status === 200) {
            if (r.headers.get("content-type") === "application/json") {
                const jr = await r.json() as ResponseData
                if (props.redirectOnCreate !== undefined) {
                    const url = props.redirectOnCreate(jr)
                    router.push(url)
                }
                else if (props.submitted !== undefined) {
                    const result = props.submitted(jr, data)
                    if (result !== undefined) {
                        if (result.message !== undefined) {
                            setErrorText(result.message)
                        }
                        else if (result.then !== undefined) {
                            result.then(r => {
                                if (r !== undefined && r.message !== undefined) setErrorText(r.message)
                            })
                        }
                    }
                }
                else if (setEditMode !== undefined) {
                    router.replace(router.asPath)
                    setEditMode(false)
                }
            }
            else if (props.submitted !== undefined) {
                props.submitted(undefined, undefined)
            }
            else if (setEditMode !== undefined) {
                router.replace(router.asPath)
                setEditMode(false)
            }
        }
        else {
            const jr = await r.json()
            setErrorText(jr.message.length > 0 ? jr.message : "Failed to save form")
        }
    }

    if (editMode === false) return <></>
    const buttons = props.buttons || []

    return <EtymographFormContext.Provider value={form}>
        <form onSubmit={form.onSubmit(saveForm)}>
            {props.children}
            <p>
                <input type="submit" value={props.saveButtonText ?? "Save"} className="uiButtonSubmit"/>
                {(props.cancelled !== undefined || setEditMode !== undefined) && <>{' '}
                    <button className="uiButton" onClick={() => props.cancelled !== undefined ? props.cancelled() : setEditMode(false)}>Cancel</button>
                </>}
                {buttons.map(b => <>{' '}<button type="button" className="uiButton" onClick={() => b.callback(form.getValues())}>{b.text}</button></>)}
            </p>
            {errorText !== "" && <div className="errorText">{errorText}</div>}
        </form>
    </EtymographFormContext.Provider>
}

import {useContext} from "react";
import {GlobalStateContext} from "@/components/Contexts";
import Select from "react-select";
import {FormFieldProps} from "@/components/FormRow";
import {useEtymographFormContext} from "@/components/EtymographForm";
import {Input} from "@mantine/core";

export default function LanguageSelect(props: FormFieldProps) {
    const form = useEtymographFormContext()
    const formValues = form.getValues()
    const globalState = useContext(GlobalStateContext)
    const languages = globalState.languages.map(
        (l) => ({value: l.shortName, label: `${l.name} (${l.shortName})`})
    )
    const value = formValues[props.id]

    return <tr>
        <td><Input.Label htmlFor={props.id}>{props.label}:</Input.Label></td>
        <td>
            <Select
                inputId={props.id}
                options={languages}
                value={languages.find((c) => c.value === value) ?? null}
                onChange={(val) => form.setFieldValue(props.id, val?.value ?? "")}
            />
        </td>
    </tr>
}

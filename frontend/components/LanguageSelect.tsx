import {Controller, useFormContext} from "react-hook-form";
import {useContext} from "react";
import {GlobalStateContext} from "@/components/Contexts";
import Select from "react-select";
import {FormFieldProps} from "@/components/FormRow";

export default function LanguageSelect(props: FormFieldProps) {
    const {control} = useFormContext()
    const globalState = useContext(GlobalStateContext)
    const languages = globalState.languages.map(
        (l) => ({value: l.shortName, label: `${l.name} (${l.shortName})`})
    )

    return <tr>
        <td><label htmlFor={props.id}>{props.label}:</label></td>
        <td>
            <Controller
                render={({ field: { onChange, value, name, ref } }) =>
                    <Select options={languages}
                            value={languages.find((c) => c.value === value)}
                            onChange={(val) => onChange(val.value)}/> }
                name={props.id}
                control={control}
            />
        </td>
    </tr>
}

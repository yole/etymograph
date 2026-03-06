import {GlobalStateContext} from "@/components/Contexts";
import {useContext} from "react";
import Select from "react-select";
import {FormFieldProps} from "@/components/FormRow";
import {useEtymographFormContext} from "@/components/EtymographForm";

interface PosSelectProps extends FormFieldProps {
    languageProp?: string;
    language?: string;
    showNone?: boolean;
    isMulti?: boolean;
}

export default function PosSelect(props: PosSelectProps) {
    const form = useEtymographFormContext()
    const formValues = form.getValues()
    const globalState = useContext(GlobalStateContext)
    const lang = props.languageProp !== undefined
        ? formValues[props.languageProp]
        : props.language
    const language = globalState.languages.find(l => l.shortName === lang)

    let pos = !language ? [] : language.pos.map((p) => ({
        value: p.abbreviation,
        label: `${p.name} (${p.abbreviation})`})
    )
    if (props.showNone) {
        pos = [{value: '', label: 'None'}].concat(pos)
    }

    const value = formValues[props.id]
    const selectedValue = props.isMulti
        ? (value === undefined ? [] : value.split(",").map(s => pos.find(r => r.value === s)).filter(Boolean))
        : pos.find((r) => r.value === value) ?? null

    return <tr>
        <td><label htmlFor={props.id}>{props.label}:</label></td>
        <td>
            <Select options={pos}
                    isMulti={props.isMulti}
                    value={selectedValue}
                    onChange={(val) => props.isMulti
                        ? form.setFieldValue(props.id, (val ?? []).map(c => c.value).join(","))
                        : form.setFieldValue(props.id, val?.value ?? "")}
            />
        </td>
    </tr>
}

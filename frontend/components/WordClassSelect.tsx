import {GlobalStateContext} from "@/components/Contexts";
import {useContext} from "react";
import Select from "react-select";
import {FormFieldProps} from "@/components/FormRow";
import {useEtymographFormContext} from "@/components/EtymographForm";

interface WordClassSelectProps extends FormFieldProps {
    languageProp?: string;
    language?: string;
    posProp?: string;
    pos?: string;
}

export default function WordClassSelect(props: WordClassSelectProps) {
    const form = useEtymographFormContext()
    const formValues = form.getValues()
    const globalState = useContext(GlobalStateContext)
    const lang = props.languageProp !== undefined
        ? formValues[props.languageProp]
        : props.language
    const pos = props.posProp !== undefined
        ? formValues[props.posProp]
        : props.pos
    const language = globalState.languages.find(l => l.shortName === lang)

    const wordClasses = !language
        ? []
        : language.wordClasses
            .filter(wc => pos === undefined || wc.pos.includes(pos))
            .flatMap((wc) => (
                wc.values.map(v => ({
                    value: v.abbreviation,
                    label: v.name === v.abbreviation ? v.name : `${v.name} (${v.abbreviation})`})
                )))

    const value = formValues[props.id]
    const selectedValues = value === undefined ? [] : value.split(" ").map(s => wordClasses.find(r => r.value === s)).filter(Boolean)

    if (wordClasses.length == 0) {
        return <></>;
    }
    return <tr>
        <td><label htmlFor={props.id}>{props.label}:</label></td>
        <td>
            <Select options={wordClasses}
                    isMulti={true}
                    value={selectedValues}
                    onChange={(val) => form.setFieldValue(props.id, (val ?? []).map(c => c.value).join(" "))}
            />
        </td>
    </tr>
}

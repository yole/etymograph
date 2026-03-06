import {GlobalStateContext} from "@/components/Contexts";
import {useContext} from "react";
import Select from "react-select";
import {FormFieldProps} from "@/components/FormRow";
import {useEtymographFormContext} from "@/components/EtymographForm";

interface GrammaticalCategorySelectProps extends FormFieldProps {
    languageProp?: string;
    language?: string;
    posProp?: string;
    pos?: string;
    isMulti?: boolean;
}

export default function GrammaticalCategorySelect(props: GrammaticalCategorySelectProps) {
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

    const grammaticalCategories = !language
        ? []
        : language.grammaticalCategories
            .filter(gc => pos === undefined || gc.pos.includes(pos))
            .map((gc) => ({
                value: gc.name,
                label: gc.name
            }))

    const isMulti = props.isMulti !== false

    const value = formValues[props.id]
    const selectedValue = isMulti
        ? (value === undefined
            ? []
            : value.split(",").map(s => s.trim()).filter(s => s.length > 0)
                .map(s => grammaticalCategories.find(r => r.value === s))
                .filter(Boolean))
        : grammaticalCategories.find((r) => r.value === value) ?? null

    if (grammaticalCategories.length == 0) {
        return <></>;
    }
    return <tr>
        <td><label htmlFor={props.id}>{props.label}:</label></td>
        <td>
            <Select options={grammaticalCategories}
                    isMulti={isMulti}
                    value={selectedValue}
                    onChange={(val) => isMulti
                        ? form.setFieldValue(props.id, (val ?? []).map(c => c.value).join(", "))
                        : form.setFieldValue(props.id, val?.value ?? "")}
            />
        </td>
    </tr>
}
